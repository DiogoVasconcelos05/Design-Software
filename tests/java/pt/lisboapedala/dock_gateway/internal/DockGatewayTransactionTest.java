package pt.lisboapedala.dock_gateway.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.DOCK_A;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.T0;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.event;

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
import pt.lisboapedala.common.valueobject.TripSummary;
import pt.lisboapedala.dock_gateway.persistence.JdbcProcessedEventRepository;
import pt.lisboapedala.dock_gateway.port.DockEventIngestion;
import pt.lisboapedala.dock_gateway.port.DockEventSubmission;
import pt.lisboapedala.dock_gateway.port.IngestOutcome;
import pt.lisboapedala.identity_access.port.CredentialValidator;
import pt.lisboapedala.payment_invoicing.port.InvoiceView;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;
import pt.lisboapedala.payment_invoicing.port.PaymentService;
import pt.lisboapedala.trip_management.internal.DefaultTripService;
import pt.lisboapedala.trip_management.internal.TripBillingDispatcher;
import pt.lisboapedala.trip_management.persistence.JdbcTripRepository;
import pt.lisboapedala.trip_management.port.TripService;
import pt.lisboapedala.trip_management.port.TripStatus;
import pt.lisboapedala.trip_management.port.TripView;

/**
 * dock_gateway com trip_management real, transações Spring e SQLite (ADR-004, secção Verificação):
 * o reenvio do mesmo encaixe não duplica viagens nem cobranças, e uma falha desfaz a unidade de trabalho inteira.
 */
class DockGatewayTransactionTest {

    private static final Credential CREDENTIAL = new Credential(CredentialKind.NAVEGANTE, "123456789");

    @TempDir
    Path dir;
    AnnotationConfigApplicationContext ctx;
    DockEventIngestion ingestion;
    TripService trips;
    DockGatewayTestFixture.FakeDocks docks;
    List<TripSummary> charges;
    MutableClock clock;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(Path.class, () -> dir);
        ctx.register(Config.class, JdbcTripRepository.class, DefaultTripService.class, TripBillingDispatcher.class,
                JdbcProcessedEventRepository.class, DockEventProcessor.class, DefaultDockEventIngestion.class,
                SimulatedDockCommandSender.class);
        ctx.refresh();
        ingestion = ctx.getBean(DockEventIngestion.class);
        trips = ctx.getBean(TripService.class);
        docks = ctx.getBean(DockGatewayTestFixture.FakeDocks.class);
        charges = ctx.getBean(Config.class).charges;
        clock = ctx.getBean(MutableClock.class);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void mesmoEncaixeEnviadoDuasVezesDaUmaViagemFechadaEUmaCobranca() {
        TripView trip = startTrip();
        DockEventRecord docked = event(DockEventType.BIKE_DOCKED, T0.plus(Duration.ofMinutes(20)));

        assertThat(ingestion.ingest(new DockEventSubmission(docked, null))).isEqualTo(IngestOutcome.PROCESSED);
        assertThat(ingestion.ingest(new DockEventSubmission(docked, null))).isEqualTo(IngestOutcome.DUPLICATE);

        assertThat(trips.getTrip(trip.tripId()).status()).isEqualTo(TripStatus.CLOSED);
        assertThat(charges).singleElement().satisfies(s -> {
            assertThat(s.tripId()).isEqualTo(trip.tripId());
            assertThat(s.durationMinutes()).isEqualTo(20);
        });
    }

    @Test
    void reenvioComHorasDeAtrasoCobraAMesmaDuracao() {
        startTrip();
        clock.advance(Duration.ofHours(6));

        ingestion.ingestBatch(List.of(
                new DockEventSubmission(event(DockEventType.BIKE_DOCKED, T0.plus(Duration.ofMinutes(20))), null)));

        assertThat(charges).singleElement().extracting(TripSummary::durationMinutes).isEqualTo(20L);
    }

    @Test
    void falhaNaUnidadeDeTrabalhoDesfazTudoEOReenvioVoltaAProcessar() {
        TripView trip = trips.requestUnlock(CREDENTIAL, DOCK_A);
        DockEventSubmission released =
                new DockEventSubmission(event(DockEventType.BIKE_RELEASED, T0), trip.tripId());
        docks.failure = new IllegalStateException("station_inventory indisponível");

        assertThatThrownBy(() -> ingestion.ingest(released)).isSameAs(docks.failure);
        assertThat(trips.getTrip(trip.tripId()).status()).isEqualTo(TripStatus.REQUESTED);

        docks.failure = null;
        assertThat(ingestion.ingest(released)).isEqualTo(IngestOutcome.PROCESSED);
        assertThat(trips.getTrip(trip.tripId()).status()).isEqualTo(TripStatus.IN_PROGRESS);
    }

    private TripView startTrip() {
        TripView trip = trips.requestUnlock(CREDENTIAL, DOCK_A);
        assertThat(ingestion.ingest(new DockEventSubmission(event(DockEventType.BIKE_RELEASED, T0), trip.tripId())))
                .isEqualTo(IngestOutcome.PROCESSED);
        return trip;
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        final List<TripSummary> charges = new ArrayList<>();

        @Bean
        DataSource dataSource(Path dir) {
            return DockGatewayTestFixture.dataSource(dir, "trip_management");
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
        MutableClock clock() {
            return new MutableClock(T0);
        }

        @Bean
        CredentialValidator credentialValidator() {
            return credential -> new PassValidation("user-1", PassValidationStatus.VALID, PassType.ANNUAL, null);
        }

        @Bean
        PaymentService paymentService() {
            return new PaymentService() {
                @Override
                public void charge(TripSummary summary) {
                    charges.add(summary);
                }

                @Override
                public void registerPaymentMethod(String userId, PaymentMethodKind kind, String token) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public List<InvoiceView> invoicesOf(String userId, int limit, int offset) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        DockGatewayTestFixture.FakeDocks docks() {
            return new DockGatewayTestFixture.FakeDocks();
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
