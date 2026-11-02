package pt.lisboapedala.maintenance_orders.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.maintenance_orders.internal.MaintenanceTestFixture.automatic;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
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
import org.springframework.transaction.support.TransactionTemplate;
import pt.lisboapedala.maintenance_orders.persistence.JdbcMaintenanceOrderRepository;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceService;

/**
 * maintenance_orders com beans e transações Spring e SQLite (ARCHITECTURE.md §7.4.3): {@code open} junta-se à
 * transação de quem chama, por isso se o registo da anomalia em fleet_health for desfeito a ordem também é.
 */
class MaintenanceTransactionTest {

    @TempDir
    Path dir;
    AnnotationConfigApplicationContext ctx;
    MaintenanceService service;
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(Path.class, () -> dir);
        ctx.register(Config.class, JdbcMaintenanceOrderRepository.class, DefaultMaintenanceService.class);
        ctx.refresh();
        service = ctx.getBean(MaintenanceService.class);
        tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void ordemAbertaNumaTransacaoDesfeitaNaoFicaGravada() {
        RuntimeException failure = new IllegalStateException("falha depois de abrir a ordem");

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.open(automatic(FaultType.BATTERY));
            throw failure;
        })).isSameAs(failure);

        assertThat(service.list(null)).isEmpty();
    }

    @Test
    void ordemAbertaNumaTransacaoConfirmadaFicaGravada() {
        tx.executeWithoutResult(status -> service.open(automatic(FaultType.BATTERY)));

        assertThat(service.list(null)).hasSize(1);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource(Path dir) {
            return MaintenanceTestFixture.dataSource(dir);
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
            return Clock.fixed(MaintenanceTestFixture.T0, ZoneOffset.UTC);
        }
    }
}
