package pt.lisboapedala.trip_management.internal;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.common.valueobject.CredentialKind;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.PassValidationStatus;
import pt.lisboapedala.common.valueobject.TripSummary;
import pt.lisboapedala.payment_invoicing.port.InvoiceView;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;
import pt.lisboapedala.payment_invoicing.port.PaymentService;
import pt.lisboapedala.trip_management.persistence.JdbcTripRepository;

/**
 * Monta trip_management sem Spring: SQLite num ficheiro temporário (ARCHITECTURE.md §7.1), relógio controlado
 * e portas falsas para identity_access, dock_gateway e payment_invoicing.
 */
final class TripTestFixture {

    static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    static final String DOCK_A = "dock-A";
    static final String DOCK_B = "dock-B";
    static final String BIKE = "bike-1";
    static final Credential CREDENTIAL = new Credential(CredentialKind.NAVEGANTE, "123456789");

    final MutableClock clock = new MutableClock(T0);
    final JdbcTripRepository repository;
    final List<String> openCommands = new ArrayList<>();
    final List<TripSummary> charges = new ArrayList<>();
    PassValidation validation = new PassValidation("user-1", PassValidationStatus.VALID, PassType.ANNUAL, null);
    RuntimeException dockFailure;
    RuntimeException paymentFailure;
    final TripBillingDispatcher dispatcher;
    final DefaultTripService service;

    TripTestFixture(Path dir) {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:sqlite:" + dir.resolve("trips.db").toAbsolutePath());
        new ResourceDatabasePopulator(new ClassPathResource("db/trip_management/schema.sql")).execute(dataSource);
        repository = new JdbcTripRepository(JdbcClient.create(dataSource));
        dispatcher = new TripBillingDispatcher(new PaymentService() {
            @Override
            public void charge(TripSummary summary) {
                if (paymentFailure != null) {
                    throw paymentFailure;
                }
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
        }, repository);
        service = new DefaultTripService(repository, credential -> validation,
                (dockId, tripId) -> {
                    if (dockFailure != null) {
                        throw dockFailure;
                    }
                    openCommands.add(dockId + "/" + tripId);
                },
                event -> dispatcher.onTripClosed((TripClosedEvent) event),
                clock);
    }

    static DockEventRecord released(String dockId, Instant at) {
        return new DockEventRecord(UUID.randomUUID(), dockId, BIKE, DockEventType.BIKE_RELEASED, at);
    }

    static DockEventRecord docked(String dockId, Instant at) {
        return new DockEventRecord(UUID.randomUUID(), dockId, BIKE, DockEventType.BIKE_DOCKED, at);
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
