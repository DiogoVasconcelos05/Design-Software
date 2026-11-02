package pt.lisboapedala.fleet_health.internal;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import pt.lisboapedala.fleet_health.persistence.JdbcActiveAnomalyRepository;
import pt.lisboapedala.fleet_health.port.TelemetryReading;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderView;
import pt.lisboapedala.maintenance_orders.port.MaintenanceService;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

/**
 * Monta fleet_health sem Spring: SQLite num ficheiro temporário (ARCHITECTURE.md §7.1), as regras reais
 * e uma porta falsa para maintenance_orders.
 */
final class FleetHealthTestFixture {

    static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    static final String BIKE = "bike-1";
    static final double MAX_TEMPERATURE = 60.0;
    static final double NORMAL_TEMPERATURE = 25.0;

    final JdbcActiveAnomalyRepository repository;
    final FakeMaintenance maintenance = new FakeMaintenance();
    final TelemetryService service;

    FleetHealthTestFixture(Path dir) {
        repository = new JdbcActiveAnomalyRepository(JdbcClient.create(dataSource(dir)));
        service = new TelemetryService(rules(), repository, new AnomalyRecorder(repository, maintenance));
    }

    static List<AnomalyRule> rules() {
        return List.of(new BatteryOverheatRule(MAX_TEMPERATURE), new BrakeFaultRule());
    }

    static DataSource dataSource(Path dir) {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:sqlite:" + dir.resolve("fleet.db").toAbsolutePath());
        new ResourceDatabasePopulator(new ClassPathResource("db/fleet_health/schema.sql")).execute(dataSource);
        return dataSource;
    }

    static TelemetryReading reading(Instant at, double batteryTemperature, boolean brakeFault) {
        return new TelemetryReading(BIKE, at, batteryTemperature, brakeFault);
    }

    static TelemetryReading normal(Instant at) {
        return reading(at, NORMAL_TEMPERATURE, false);
    }

    /** Regista os pedidos de abertura; lança {@link #failure} se estiver definida. */
    static final class FakeMaintenance implements MaintenanceService {
        final List<OpenMaintenanceOrder> opened = new ArrayList<>();
        RuntimeException failure;

        @Override
        public MaintenanceOrderView open(OpenMaintenanceOrder command) {
            if (failure != null) {
                throw failure;
            }
            opened.add(command);
            return new MaintenanceOrderView(UUID.randomUUID(), command.bikeId(), command.faultType(),
                    command.origin(), MaintenanceOrderStatus.OPEN, command.description(), command.reportedBy(),
                    null, T0, null, null, null);
        }

        @Override
        public List<MaintenanceOrderView> list(MaintenanceOrderStatus status) {
            throw new UnsupportedOperationException("fleet_health só abre ordens");
        }

        @Override
        public MaintenanceOrderView assign(UUID orderId, String technicianId) {
            throw new UnsupportedOperationException("fleet_health só abre ordens");
        }

        @Override
        public MaintenanceOrderView start(UUID orderId) {
            throw new UnsupportedOperationException("fleet_health só abre ordens");
        }

        @Override
        public MaintenanceOrderView complete(UUID orderId, String notes) {
            throw new UnsupportedOperationException("fleet_health só abre ordens");
        }
    }
}
