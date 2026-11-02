package pt.lisboapedala.fleet_health.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.BIKE;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.NORMAL_TEMPERATURE;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.T0;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.reading;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import pt.lisboapedala.fleet_health.persistence.ActiveAnomalyRepository;
import pt.lisboapedala.fleet_health.persistence.JdbcActiveAnomalyRepository;
import pt.lisboapedala.fleet_health.port.TelemetryIngestion;

/**
 * fleet_health com beans e transações Spring e SQLite (ARCHITECTURE.md §7.4.3): se a ordem de manutenção não
 * puder ser aberta, a anomalia não fica registada e a leitura seguinte volta a tentar.
 */
class FleetHealthTransactionTest {

    @TempDir
    Path dir;
    AnnotationConfigApplicationContext ctx;
    TelemetryIngestion ingestion;
    ActiveAnomalyRepository anomalies;
    FleetHealthTestFixture.FakeMaintenance maintenance;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("lisboapedala.fleet.battery-max-temperature-celsius", "60")));
        ctx.registerBean(Path.class, () -> dir);
        ctx.register(Config.class, JdbcActiveAnomalyRepository.class, BatteryOverheatRule.class,
                BrakeFaultRule.class, AnomalyRecorder.class, TelemetryService.class);
        ctx.refresh();
        ingestion = ctx.getBean(TelemetryIngestion.class);
        anomalies = ctx.getBean(ActiveAnomalyRepository.class);
        maintenance = ctx.getBean(FleetHealthTestFixture.FakeMaintenance.class);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void falhaAAbrirOrdemDesfazORegistoEALeituraSeguinteVoltaATentar() {
        maintenance.failure = new IllegalStateException("maintenance_orders indisponível");

        assertThatThrownBy(() -> ingestion.ingest(reading(T0, 75, false))).isSameAs(maintenance.failure);
        assertThat(anomalies.findByBike(BIKE)).isEmpty();

        maintenance.failure = null;
        ingestion.ingest(reading(T0.plus(Duration.ofSeconds(30)), 75, false));

        assertThat(maintenance.opened).hasSize(1);
        assertThat(anomalies.findByBike(BIKE)).hasSize(1);
    }

    @Test
    void asRegrasSaoDescobertasComoBeans() {
        ingestion.ingest(reading(T0, NORMAL_TEMPERATURE, true));
        ingestion.ingest(reading(T0.plus(Duration.ofSeconds(30)), 75, true));

        assertThat(maintenance.opened).hasSize(2);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        DataSource dataSource(Path dir) {
            return FleetHealthTestFixture.dataSource(dir);
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
        FleetHealthTestFixture.FakeMaintenance maintenance() {
            return new FleetHealthTestFixture.FakeMaintenance();
        }
    }
}
