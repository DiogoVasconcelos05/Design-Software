package pt.lisboapedala.maintenance_orders.internal;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import pt.lisboapedala.maintenance_orders.persistence.JdbcMaintenanceOrderRepository;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

/**
 * Monta maintenance_orders sem Spring: SQLite num ficheiro temporário (ARCHITECTURE.md §7.1) e um relógio
 * controlado pelo teste (FF-16).
 */
final class MaintenanceTestFixture {

    static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    static final String BIKE = "bike-1";
    static final String TECHNICIAN = "tec-7";

    final MutableClock clock = new MutableClock(T0);
    final JdbcMaintenanceOrderRepository repository;
    final DefaultMaintenanceService service;

    MaintenanceTestFixture(Path dir) {
        repository = new JdbcMaintenanceOrderRepository(JdbcClient.create(dataSource(dir)));
        service = new DefaultMaintenanceService(repository, clock);
    }

    static DataSource dataSource(Path dir) {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:sqlite:" + dir.resolve("maintenance.db").toAbsolutePath());
        new ResourceDatabasePopulator(new ClassPathResource("db/maintenance_orders/schema.sql")).execute(dataSource);
        return dataSource;
    }

    static OpenMaintenanceOrder userReport(FaultType faultType) {
        return new OpenMaintenanceOrder(BIKE, faultType, "Travão traseiro não trava", "user-42",
                MaintenanceOrigin.USER_REPORT);
    }

    static OpenMaintenanceOrder automatic(FaultType faultType) {
        return new OpenMaintenanceOrder(BIKE, faultType, "Temperatura da bateria 72.5 °C", "fleet_health",
                MaintenanceOrigin.AUTOMATIC);
    }

    /** Relógio que o teste faz avançar. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
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
