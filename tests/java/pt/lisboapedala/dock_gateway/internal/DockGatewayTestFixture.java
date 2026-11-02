package pt.lisboapedala.dock_gateway.internal;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.dock_gateway.persistence.JdbcProcessedEventRepository;
import pt.lisboapedala.station_inventory.port.DockOccupancyPort;
import pt.lisboapedala.trip_management.port.TripEventOutcome;
import pt.lisboapedala.trip_management.port.TripService;
import pt.lisboapedala.trip_management.port.TripView;

/**
 * Monta dock_gateway sem Spring: SQLite num ficheiro temporário (ARCHITECTURE.md §7.1), relógio fixo
 * e portas falsas para trip_management e station_inventory.
 */
final class DockGatewayTestFixture {

    static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    static final String DOCK_A = "dock-A";
    static final String BIKE = "bike-1";

    final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    final JdbcProcessedEventRepository repository;
    final FakeDocks docks = new FakeDocks();
    final FakeTrips trips = new FakeTrips();
    final DockEventProcessor processor;
    final DefaultDockEventIngestion ingestion;

    DockGatewayTestFixture(Path dir) {
        repository = new JdbcProcessedEventRepository(JdbcClient.create(dataSource(dir)));
        processor = new DockEventProcessor(repository, docks, trips, clock);
        ingestion = new DefaultDockEventIngestion(processor);
    }

    static DataSource dataSource(Path dir, String... modules) {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:sqlite:" + dir.resolve("dock.db").toAbsolutePath());
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/dock_gateway/schema.sql"));
        for (String module : modules) {
            populator.addScript(new ClassPathResource("db/" + module + "/schema.sql"));
        }
        populator.execute(dataSource);
        return dataSource;
    }

    static DockEventRecord event(DockEventType type, Instant at) {
        return new DockEventRecord(UUID.randomUUID(), DOCK_A, BIKE, type, at);
    }

    /** Regista as chamadas a station_inventory pela ordem em que acontecem. */
    static final class FakeDocks implements DockOccupancyPort {
        final List<String> calls = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void markDockFree(String dockId, Instant occurredAt) {
            record("free " + dockId);
        }

        @Override
        public void markDockOccupied(String dockId, String bikeId, Instant occurredAt) {
            record("occupied " + dockId + " " + bikeId);
        }

        @Override
        public void markDockLocked(String dockId, boolean locked, Instant occurredAt) {
            record((locked ? "locked " : "unlocked ") + dockId);
        }

        private void record(String call) {
            if (failure != null) {
                throw failure;
            }
            calls.add(call);
        }
    }

    /** Só os métodos usados por dock_gateway; devolve {@link #outcome} e regista as chamadas. */
    static final class FakeTrips implements TripService {
        final List<String> calls = new ArrayList<>();
        TripEventOutcome outcome = TripEventOutcome.APPLIED;

        @Override
        public TripEventOutcome startTrip(UUID tripId, DockEventRecord event) {
            calls.add("start " + tripId + " " + event.eventId());
            return outcome;
        }

        @Override
        public TripEventOutcome closeTrip(DockEventRecord event) {
            calls.add("close " + event.eventId());
            return outcome;
        }

        @Override
        public TripView requestUnlock(Credential credential, String dockId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TripView getTrip(UUID tripId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TripView> history(String userId, int limit, int offset) {
            throw new UnsupportedOperationException();
        }
    }
}
