package pt.lisboapedala.payment_invoicing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.payment_invoicing.internal.PaymentTestFixture.MAX_ATTEMPTS;
import static pt.lisboapedala.payment_invoicing.internal.PaymentTestFixture.USER;
import static pt.lisboapedala.payment_invoicing.internal.PaymentTestFixture.trip;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.common.valueobject.TripSummary;
import pt.lisboapedala.payment_invoicing.persistence.ChargeEntity;
import pt.lisboapedala.payment_invoicing.persistence.ChargeStatus;
import pt.lisboapedala.payment_invoicing.port.InvoiceView;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;
import pt.lisboapedala.payment_invoicing.port.TaxSubmissionStatus;
import pt.lisboapedala.tariff_pricing.port.PricingRequest;

class DefaultPaymentServiceTest {

    @TempDir
    Path dir;
    PaymentTestFixture f;

    @BeforeEach
    void setUp() {
        f = new PaymentTestFixture(dir);
        f.service.registerPaymentMethod(USER, PaymentMethodKind.MB_WAY, "tok-mbway");
    }

    @Test
    void cobraPeloTarifarioEmiteFaturaEComunicaAAT() {
        TripSummary summary = trip(52);

        f.service.charge(summary);

        assertThat(f.tariff.requests).containsExactly(new PricingRequest(PassType.OCCASIONAL, 52));
        assertThat(f.psp.orders).singleElement().satisfies(o -> {
            assertThat(o.idempotencyKey()).isEqualTo(summary.tripId());
            assertThat(o.kind()).isEqualTo(PaymentMethodKind.MB_WAY);
            assertThat(o.token()).isEqualTo("tok-mbway");
            assertThat(o.amountCents()).isEqualTo(150);
        });
        assertThat(f.service.invoicesOf(USER, 20, 0)).singleElement().satisfies(i -> {
            assertThat(i.tripId()).isEqualTo(summary.tripId());
            assertThat(i.invoiceNumber()).isEqualTo("FT LP/1");
            assertThat(i.amount()).isEqualByComparingTo("1.50");
            assertThat(i.atStatus()).isEqualTo(TaxSubmissionStatus.SUBMITTED);
            assertThat(i.atReference()).isEqualTo("at-FT LP/1");
        });
        assertThat(f.charges.findByTripId(summary.tripId())).get()
                .extracting(ChargeEntity::status).isEqualTo(ChargeStatus.PAID);
    }

    @Test
    void pedidoRepetidoParaAMesmaViagemNaoCobraNemFaturaDuasVezes() {
        TripSummary summary = trip(20);

        f.service.charge(summary);
        f.service.charge(summary);
        f.runRetryJob();

        assertThat(f.psp.orders).hasSize(1);
        assertThat(f.taxAuthority.documents).hasSize(1);
        assertThat(f.service.invoicesOf(USER, 20, 0)).hasSize(1);
    }

    @Test
    void semMeioDePagamentoFicaPorCobrarEOJobRetomaDepoisDoRegisto() {
        TripSummary summary = new TripSummary(trip(20).tripId(), "user-2", PassType.ANNUAL,
                PaymentTestFixture.T0, PaymentTestFixture.T0.plusSeconds(1200), 20);

        f.service.charge(summary);

        assertThat(f.psp.orders).isEmpty();
        assertThat(f.charges.findByTripId(summary.tripId())).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(ChargeStatus.FAILED);
            assertThat(c.attempts()).isEqualTo(1);
        });

        f.service.registerPaymentMethod("user-2", PaymentMethodKind.CARD, "tok-card");
        f.runRetryJob();

        assertThat(f.psp.orders).singleElement().extracting(PaymentOrder::kind).isEqualTo(PaymentMethodKind.CARD);
        assertThat(f.service.invoicesOf("user-2", 20, 0)).hasSize(1);
    }

    @Test
    void recusaDoPspETentadaDeNovoAteAoLimite() {
        f.psp.failure = new BusinessRuleException(ErrorCode.PAYMENT_FAILED, "saldo insuficiente");
        TripSummary summary = trip(20);

        f.service.charge(summary);
        for (int i = 0; i < MAX_ATTEMPTS + 2; i++) {
            f.runRetryJob();
        }

        assertThat(f.psp.orders).hasSize(MAX_ATTEMPTS);
        assertThat(f.charges.findByTripId(summary.tripId())).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(ChargeStatus.FAILED);
            assertThat(c.lastError()).isEqualTo("saldo insuficiente");
        });
        assertThat(f.service.invoicesOf(USER, 20, 0)).isEmpty();
    }

    @Test
    void cobrancaQueFalhaUmaVezEPagaNoReenvioComAMesmaChaveDeIdempotencia() {
        f.psp.failure = new IllegalStateException("timeout");
        TripSummary summary = trip(20);
        f.service.charge(summary);

        f.psp.failure = null;
        f.runRetryJob();

        assertThat(f.psp.orders).extracting(PaymentOrder::idempotencyKey)
                .containsExactly(summary.tripId(), summary.tripId());
        assertThat(f.service.invoicesOf(USER, 20, 0)).hasSize(1);
    }

    @Test
    void jobNaoRetomaAntesDoIntervaloDeReenvio() {
        f.psp.failure = new IllegalStateException("timeout");
        f.service.charge(trip(20));

        f.clock.advance(Duration.ofSeconds(10));
        f.job.retryPending();

        assertThat(f.psp.orders).hasSize(1);
    }

    @Test
    void falhaDaATDeixaAFaturaPendenteEOJobReenvia() {
        f.taxAuthority.failure = new IllegalStateException("AT indisponível");
        f.service.charge(trip(20));

        assertThat(f.service.invoicesOf(USER, 20, 0)).singleElement()
                .extracting(InvoiceView::atStatus).isEqualTo(TaxSubmissionStatus.PENDING);

        f.taxAuthority.failure = null;
        f.runRetryJob();

        assertThat(f.taxAuthority.documents).hasSize(2)
                .extracting(TaxDocument::invoiceNumber).containsOnly("FT LP/1");
        assertThat(f.service.invoicesOf(USER, 20, 0)).singleElement()
                .extracting(InvoiceView::atStatus).isEqualTo(TaxSubmissionStatus.SUBMITTED);
        assertThat(f.psp.orders).hasSize(1);
    }

    @Test
    void viagemSemCustoNaoChamaOPspNemEmiteFatura() {
        f.tariff.price = BigDecimal.ZERO;
        TripSummary summary = trip(10);

        f.service.charge(summary);

        assertThat(f.psp.orders).isEmpty();
        assertThat(f.service.invoicesOf(USER, 20, 0)).isEmpty();
        assertThat(f.charges.findByTripId(summary.tripId())).get()
                .extracting(ChargeEntity::status).isEqualTo(ChargeStatus.PAID);
    }

    @Test
    void valorDoTarifarioEArredondadoAoCentimo() {
        f.tariff.price = new BigDecimal("2.345");

        f.service.charge(trip(20));

        assertThat(f.psp.orders).singleElement().extracting(PaymentOrder::amountCents).isEqualTo(235L);
    }

    @Test
    void novoMeioDePagamentoSubstituiOAnterior() {
        f.service.registerPaymentMethod(USER, PaymentMethodKind.CARD, "tok-card");

        f.service.charge(trip(20));

        assertThat(f.psp.orders).singleElement().satisfies(o -> {
            assertThat(o.kind()).isEqualTo(PaymentMethodKind.CARD);
            assertThat(o.token()).isEqualTo("tok-card");
        });
    }

    @Test
    void historicoDeFaturasEOMaisRecentePrimeiroEPaginado() {
        TripSummary first = trip(20);
        TripSummary second = trip(30);
        f.service.charge(first);
        f.clock.advance(Duration.ofMinutes(5));
        f.service.charge(second);

        assertThat(f.service.invoicesOf(USER, 20, 0)).extracting(InvoiceView::tripId)
                .containsExactly(second.tripId(), first.tripId());
        assertThat(f.service.invoicesOf(USER, 1, 1)).extracting(InvoiceView::tripId)
                .containsExactly(first.tripId());
        assertThat(f.service.invoicesOf("outro", 20, 0)).isEmpty();
    }

    @Test
    void pedidosInvalidosDaoValidation() {
        List<Runnable> invalid = List.of(
                () -> f.service.registerPaymentMethod(" ", PaymentMethodKind.CARD, "tok"),
                () -> f.service.registerPaymentMethod(USER, null, "tok"),
                () -> f.service.registerPaymentMethod(USER, PaymentMethodKind.CARD, ""),
                () -> f.service.invoicesOf(USER, 0, 0),
                () -> f.service.invoicesOf(USER, 101, 0),
                () -> f.service.invoicesOf(USER, 20, -1),
                () -> f.service.invoicesOf(null, 20, 0));
        for (Runnable call : invalid) {
            assertThatThrownBy(call::run)
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
        }
    }
}
