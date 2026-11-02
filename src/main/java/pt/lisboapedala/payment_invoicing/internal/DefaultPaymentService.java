package pt.lisboapedala.payment_invoicing.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.common.valueobject.TripSummary;
import pt.lisboapedala.payment_invoicing.persistence.ChargeEntity;
import pt.lisboapedala.payment_invoicing.persistence.ChargeStatus;
import pt.lisboapedala.payment_invoicing.persistence.InvoiceEntity;
import pt.lisboapedala.payment_invoicing.persistence.InvoiceRepository;
import pt.lisboapedala.payment_invoicing.persistence.PaymentMethodEntity;
import pt.lisboapedala.payment_invoicing.port.InvoiceView;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;
import pt.lisboapedala.payment_invoicing.port.PaymentService;
import pt.lisboapedala.tariff_pricing.port.PricingRequest;
import pt.lisboapedala.tariff_pricing.port.TariffPricing;

/**
 * Cobrança, faturação e histórico de faturas (ARCHITECTURE.md §7.3 e §7.4.2, ADR-003).
 */
@Service
public class DefaultPaymentService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentService.class);

    private final TariffPricing tariff;
    private final PaymentLedger ledger;
    private final ChargeSettlement settlement;
    private final InvoiceRepository invoices;
    private final Clock clock;

    public DefaultPaymentService(TariffPricing tariff, PaymentLedger ledger, ChargeSettlement settlement,
                                 InvoiceRepository invoices, Clock clock) {
        this.tariff = tariff;
        this.ledger = ledger;
        this.settlement = settlement;
        this.invoices = invoices;
        this.clock = clock;
    }

    /**
     * Regista a cobrança (idempotente por tripId, ADR-004 §4) e faz logo a primeira tentativa. Corre fora de
     * qualquer transação do chamador ({@code NOT_SUPPORTED}): trip_management chama-a depois do commit do fecho.
     * Depois de registada, uma falha na tentativa fica para o job de reenvio e não é propagada.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void charge(TripSummary summary) {
        Objects.requireNonNull(summary, "summary não pode ser nulo");
        long amountCents = Money.toCents(
                tariff.price(new PricingRequest(summary.passType(), summary.durationMinutes())));
        Instant now = Instant.now(clock);
        ChargeEntity charge = new ChargeEntity(summary.tripId(), summary.userId(), summary.passType(),
                summary.durationMinutes(), amountCents, ChargeStatus.PENDING, 0, null, null, now, now);
        if (!ledger.open(charge)) {
            log.info("Viagem {} já tem cobrança; pedido repetido ignorado", summary.tripId());
            return;
        }
        try {
            settlement.attempt(charge);
        } catch (RuntimeException e) {
            log.warn("Tentativa de cobrança da viagem {} interrompida; fica para o job de reenvio",
                    summary.tripId(), e);
        }
    }

    @Override
    public void registerPaymentMethod(String userId, PaymentMethodKind kind, String token) {
        requireText(userId, "userId");
        if (kind == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "kind é obrigatório");
        }
        requireText(token, "token");
        ledger.savePaymentMethod(new PaymentMethodEntity(userId, kind, token, Instant.now(clock)));
        log.info("Meio de pagamento {} registado para o utilizador {}", kind, userId);
    }

    @Override
    public List<InvoiceView> invoicesOf(String userId, int limit, int offset) {
        requireText(userId, "userId");
        Paging.validate(limit, offset);
        return invoices.findByUser(userId, limit, offset).stream()
                .map(DefaultPaymentService::toView)
                .toList();
    }

    private static InvoiceView toView(InvoiceEntity invoice) {
        return new InvoiceView(invoice.invoiceId(), InvoiceNumbers.format(invoice.number()), invoice.tripId(),
                invoice.userId(), Money.toEuros(invoice.amountCents()), invoice.issuedAt(), invoice.atStatus(),
                invoice.atReference());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, name + " é obrigatório");
        }
    }
}
