package pt.lisboapedala.payment_invoicing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.lisboapedala.payment_invoicing.internal.PaymentTestFixture.T0;
import static pt.lisboapedala.payment_invoicing.internal.PaymentTestFixture.USER;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.common.valueobject.CredentialKind;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.PassValidationStatus;
import pt.lisboapedala.dock_gateway.internal.DefaultDockEventIngestion;
import pt.lisboapedala.dock_gateway.internal.DockEventProcessor;
import pt.lisboapedala.dock_gateway.internal.SimulatedDockCommandSender;
import pt.lisboapedala.dock_gateway.persistence.JdbcProcessedEventRepository;
import pt.lisboapedala.dock_gateway.port.DockEventIngestion;
import pt.lisboapedala.dock_gateway.port.DockEventSubmission;
import pt.lisboapedala.dock_gateway.port.IngestOutcome;
import pt.lisboapedala.identity_access.port.CredentialValidator;
import pt.lisboapedala.payment_invoicing.persistence.JdbcChargeRepository;
import pt.lisboapedala.payment_invoicing.persistence.JdbcInvoiceRepository;
import pt.lisboapedala.payment_invoicing.persistence.JdbcPaymentMethodRepository;
import pt.lisboapedala.payment_invoicing.port.InvoiceView;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;
import pt.lisboapedala.payment_invoicing.port.PaymentService;
import pt.lisboapedala.payment_invoicing.port.TaxSubmissionStatus;
import pt.lisboapedala.station_inventory.port.DockOccupancyPort;
import pt.lisboapedala.tariff_pricing.port.TariffPricing;
import pt.lisboapedala.trip_management.internal.DefaultTripService;
import pt.lisboapedala.trip_management.internal.TripBillingDispatcher;
import pt.lisboapedala.trip_management.persistence.JdbcTripRepository;
import pt.lisboapedala.trip_management.port.TripService;
import pt.lisboapedala.trip_management.port.TripView;

/**
 * payment_invoicing com dock_gateway e trip_management reais, transações Spring e SQLite (ADR-004, secção
 * Verificação): o mesmo encaixe enviado duas vezes dá 1 viagem fechada, 1 cobrança e 1 fatura, e a cobrança,
 * pedida depois do commit do fecho, fica persistida.
 */
class PaymentAfterTripCloseTest {

    private static final Credential CREDENTIAL = new Credential(CredentialKind.NAVEGANTE, "123456789");
    private static final String DOCK = "dock-A";
    private static final String BIKE = "bike-1";

    @TempDir
    Path dir;
    AnnotationConfigApplicationContext ctx;
    DockEventIngestion ingestion;
    TripService trips;
    PaymentService payments;
    PaymentTestFixture.FakePsp psp;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(Path.class, () -> dir);
        ctx.register(Config.class, JdbcTripRepository.class, DefaultTripService.class, TripBillingDispatcher.class,
                JdbcProcessedEventRepository.class, DockEventProcessor.class, DefaultDockEventIngestion.class,
                SimulatedDockCommandSender.class, JdbcChargeRepository.class, JdbcInvoiceRepository.class,
                JdbcPaymentMethodRepository.class, PaymentLedger.class, InvoiceSubmitter.class,
                ChargeSettlement.class, DefaultPaymentService.class, SimulatedTaxAuthority.class);
        ctx.refresh();
        ingestion = ctx.getBean(DockEventIngestion.class);
        trips = ctx.getBean(TripService.class);
        payments = ctx.getBean(PaymentService.class);
        psp = ctx.getBean(PaymentTestFixture.FakePsp.class);
        payments.registerPaymentMethod(USER, PaymentMethodKind.MB_WAY, "tok-mbway");
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void mesmoEncaixeEnviadoDuasVezesDaUmaCobrancaEUmaFatura() {
        TripView trip = trips.requestUnlock(CREDENTIAL, DOCK);
        ingestion.ingest(new DockEventSubmission(event(DockEventType.BIKE_RELEASED, T0), trip.tripId()));
        DockEventRecord docked = event(DockEventType.BIKE_DOCKED, T0.plus(Duration.ofMinutes(20)));

        assertThat(ingestion.ingest(new DockEventSubmission(docked, null))).isEqualTo(IngestOutcome.PROCESSED);
        assertThat(ingestion.ingest(new DockEventSubmission(docked, null))).isEqualTo(IngestOutcome.DUPLICATE);

        assertThat(psp.orders).singleElement().extracting(PaymentOrder::idempotencyKey).isEqualTo(trip.tripId());
        assertThat(payments.invoicesOf(USER, 20, 0)).singleElement().satisfies(i -> {
            assertThat(i.tripId()).isEqualTo(trip.tripId());
            assertThat(i.amount()).isEqualByComparingTo("1.50");
            assertThat(i.atStatus()).isEqualTo(TaxSubmissionStatus.SUBMITTED);
        });
    }

    @Test
    void cobrancaRepetidaPeloOutboxDeTripManagementNaoDuplicaAFatura() {
        TripView trip = trips.requestUnlock(CREDENTIAL, DOCK);
        ingestion.ingest(new DockEventSubmission(event(DockEventType.BIKE_RELEASED, T0), trip.tripId()));
        ingestion.ingest(new DockEventSubmission(event(DockEventType.BIKE_DOCKED, T0.plusSeconds(600)), null));

        ctx.getBean(JdbcClient.class).sql("UPDATE trips SET billing_dispatched = 0").update();
        ctx.getBean(TripBillingDispatcher.class).retryPendingBilling();

        assertThat(psp.orders).hasSize(1);
        assertThat(payments.invoicesOf(USER, 20, 0)).extracting(InvoiceView::tripId).containsExactly(trip.tripId());
    }

    private static DockEventRecord event(DockEventType type, Instant at) {
        return new DockEventRecord(UUID.randomUUID(), DOCK, BIKE, type, at);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource(Path dir) {
            return PaymentTestFixture.dataSource(dir, "trip_management", "dock_gateway");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcClient jdbcClient(DataSource dataSource) {
            return JdbcClient.create(dataSource);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(T0, ZoneOffset.UTC);
        }

        @Bean
        CredentialValidator credentialValidator() {
            return credential -> new PassValidation(USER, PassValidationStatus.VALID, PassType.OCCASIONAL, null);
        }

        @Bean
        TariffPricing tariffPricing() {
            return request -> new BigDecimal("1.50");
        }

        @Bean
        PaymentTestFixture.FakePsp psp() {
            return new PaymentTestFixture.FakePsp();
        }

        @Bean
        DockOccupancyPort docks() {
            return new DockOccupancyPort() {
                @Override
                public void markDockFree(String dockId, Instant occurredAt) {
                }

                @Override
                public void markDockOccupied(String dockId, String bikeId, Instant occurredAt) {
                }

                @Override
                public void markDockLocked(String dockId, boolean locked, Instant occurredAt) {
                }
            };
        }
    }
}
