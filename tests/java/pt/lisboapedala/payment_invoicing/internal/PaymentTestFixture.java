package pt.lisboapedala.payment_invoicing.internal;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.common.valueobject.TripSummary;
import pt.lisboapedala.payment_invoicing.persistence.JdbcChargeRepository;
import pt.lisboapedala.payment_invoicing.persistence.JdbcInvoiceRepository;
import pt.lisboapedala.payment_invoicing.persistence.JdbcPaymentMethodRepository;
import pt.lisboapedala.tariff_pricing.port.PricingRequest;
import pt.lisboapedala.tariff_pricing.port.TariffPricing;

/**
 * Monta payment_invoicing sem Spring: SQLite num ficheiro temporário (ARCHITECTURE.md §7.1), relógio controlado
 * e portas falsas para tariff_pricing, PSP e AT.
 */
final class PaymentTestFixture {

    static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    static final String USER = "user-1";
    static final int MAX_ATTEMPTS = 3;
    static final long RETRY_INTERVAL_SECONDS = 60;

    final MutableClock clock = new MutableClock(T0);
    final FakeTariff tariff = new FakeTariff();
    final FakePsp psp = new FakePsp();
    final FakeTaxAuthority taxAuthority = new FakeTaxAuthority();
    final JdbcChargeRepository charges;
    final JdbcInvoiceRepository invoices;
    final DefaultPaymentService service;
    final PaymentRetryJob job;

    PaymentTestFixture(Path dir) {
        JdbcClient jdbc = JdbcClient.create(dataSource(dir));
        charges = new JdbcChargeRepository(jdbc);
        invoices = new JdbcInvoiceRepository(jdbc);
        JdbcPaymentMethodRepository methods = new JdbcPaymentMethodRepository(jdbc);
        PaymentLedger ledger = new PaymentLedger(charges, invoices, methods, clock);
        InvoiceSubmitter submitter = new InvoiceSubmitter(taxAuthority, ledger);
        ChargeSettlement settlement = new ChargeSettlement(methods, psp, ledger, submitter);
        service = new DefaultPaymentService(tariff, ledger, settlement, invoices, clock);
        job = new PaymentRetryJob(charges, invoices, settlement, submitter, clock, RETRY_INTERVAL_SECONDS,
                MAX_ATTEMPTS);
    }

    static DataSource dataSource(Path dir, String... otherModules) {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:sqlite:" + dir.resolve("payments.db").toAbsolutePath());
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/payment_invoicing/schema.sql"));
        for (String module : otherModules) {
            populator.addScript(new ClassPathResource("db/" + module + "/schema.sql"));
        }
        populator.execute(dataSource);
        return dataSource;
    }

    static TripSummary trip(long minutes) {
        return new TripSummary(UUID.randomUUID(), USER, PassType.OCCASIONAL, T0,
                T0.plus(Duration.ofMinutes(minutes)), minutes);
    }

    /** Avança o relógio para lá do intervalo de reenvio e corre o job. */
    void runRetryJob() {
        clock.advance(Duration.ofSeconds(RETRY_INTERVAL_SECONDS + 1));
        job.retryPending();
    }

    /** Devolve sempre {@link #price} e regista os pedidos. */
    static final class FakeTariff implements TariffPricing {
        final List<PricingRequest> requests = new ArrayList<>();
        BigDecimal price = new BigDecimal("1.50");

        @Override
        public BigDecimal price(PricingRequest request) {
            requests.add(request);
            return price;
        }
    }

    /** Regista as cobranças; lança {@link #failure} se estiver definida. */
    static final class FakePsp implements PaymentGateway {
        final List<PaymentOrder> orders = new ArrayList<>();
        RuntimeException failure;

        @Override
        public String collect(PaymentOrder order) {
            orders.add(order);
            if (failure != null) {
                throw failure;
            }
            return "psp-" + order.idempotencyKey();
        }
    }

    /** Regista as comunicações; lança {@link #failure} se estiver definida. */
    static final class FakeTaxAuthority implements TaxAuthorityGateway {
        final List<TaxDocument> documents = new ArrayList<>();
        RuntimeException failure;

        @Override
        public String submit(TaxDocument document) {
            documents.add(document);
            if (failure != null) {
                throw failure;
            }
            return "at-" + document.invoiceNumber();
        }
    }

    /** Relógio que os testes fazem avançar; substitui Instant.now() (FF-16). */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
