package pt.lisboapedala.fleet_health.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.BIKE;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.NORMAL_TEMPERATURE;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.T0;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.normal;
import static pt.lisboapedala.fleet_health.internal.FleetHealthTestFixture.reading;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.lisboapedala.common.exception.DomainException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.fleet_health.persistence.ActiveAnomalyEntity;
import pt.lisboapedala.fleet_health.port.TelemetryReading;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

class TelemetryServiceTest {

    private static final Duration PERIOD = Duration.ofSeconds(30);

    @TempDir
    Path dir;
    FleetHealthTestFixture f;

    @BeforeEach
    void setUp() {
        f = new FleetHealthTestFixture(dir);
    }

    @Test
    void leituraNormalNaoAbreOrdensNemEscreve() {
        f.service.ingest(normal(T0));

        assertThat(f.maintenance.opened).isEmpty();
        assertThat(f.repository.findByBike(BIKE)).isEmpty();
    }

    @Test
    void bateriaSobreaquecidaAbreOrdemAutomaticaDeBateria() {
        f.service.ingest(reading(T0, 72.5, false));

        assertThat(f.maintenance.opened).singleElement().satisfies(order -> {
            assertThat(order.bikeId()).isEqualTo(BIKE);
            assertThat(order.faultType()).isEqualTo(FaultType.BATTERY);
            assertThat(order.origin()).isEqualTo(MaintenanceOrigin.AUTOMATIC);
            assertThat(order.reportedBy()).isEqualTo(AnomalyRecorder.REPORTED_BY);
            assertThat(order.description()).contains("72.5");
        });
        assertThat(f.repository.findByBike(BIKE)).containsExactly(
                new ActiveAnomalyEntity(BIKE, AnomalyType.BATTERY_OVERHEAT.name(), T0));
    }

    @Test
    void falhaDeTravoesAbreOrdemDeTravoes() {
        f.service.ingest(reading(T0, NORMAL_TEMPERATURE, true));

        assertThat(f.maintenance.opened).extracting(OpenMaintenanceOrder::faultType).containsExactly(FaultType.BRAKES);
    }

    @Test
    void duasAnomaliasNaMesmaLeituraAbremUmaOrdemCada() {
        f.service.ingest(reading(T0, 80, true));

        assertThat(f.maintenance.opened).extracting(OpenMaintenanceOrder::faultType)
                .containsExactlyInAnyOrder(FaultType.BATTERY, FaultType.BRAKES);
    }

    @Test
    void anomaliaQuePersisteNasLeiturasSeguintesSoAbreUmaOrdem() {
        for (int i = 0; i < 5; i++) {
            f.service.ingest(reading(T0.plus(PERIOD.multipliedBy(i)), NORMAL_TEMPERATURE, true));
        }

        assertThat(f.maintenance.opened).hasSize(1);
    }

    @Test
    void anomaliaResolvidaQueReaparecePedeNovaOrdem() {
        f.service.ingest(reading(T0, NORMAL_TEMPERATURE, true));
        f.service.ingest(normal(T0.plus(PERIOD)));

        assertThat(f.repository.findByBike(BIKE)).isEmpty();

        f.service.ingest(reading(T0.plus(PERIOD.multipliedBy(2)), NORMAL_TEMPERATURE, true));

        assertThat(f.maintenance.opened).hasSize(2);
    }

    @Test
    void leituraAntigaReenviadaForaDeOrdemNaoResolveAAnomalia() {
        f.service.ingest(reading(T0, NORMAL_TEMPERATURE, true));
        f.service.ingest(normal(T0.minus(PERIOD)));

        assertThat(f.repository.findByBike(BIKE)).hasSize(1);
    }

    @Test
    void anomaliasDeBicicletasDiferentesSaoIndependentes() {
        f.service.ingest(new TelemetryReading("bike-2", T0, NORMAL_TEMPERATURE, true));
        f.service.ingest(reading(T0, NORMAL_TEMPERATURE, true));

        assertThat(f.maintenance.opened).extracting(OpenMaintenanceOrder::bikeId).containsExactly("bike-2", BIKE);
    }

    @Test
    void leiturasInvalidasDaoValidation() {
        for (TelemetryReading invalid : new TelemetryReading[] {
                null,
                new TelemetryReading(" ", T0, NORMAL_TEMPERATURE, false),
                new TelemetryReading(BIKE, null, NORMAL_TEMPERATURE, false),
                new TelemetryReading(BIKE, T0, Double.NaN, false)}) {
            assertThatThrownBy(() -> f.service.ingest(invalid))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION);
        }
        assertThat(f.maintenance.opened).isEmpty();
    }

    @Test
    void duasRegrasParaOMesmoTipoSaoRejeitadas() {
        List<AnomalyRule> rules = List.of(new BrakeFaultRule(), new BrakeFaultRule());

        assertThatThrownBy(() -> new TelemetryService(rules, f.repository, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void temperaturaNoLimiteJaEAnomalia() {
        BatteryOverheatRule rule = new BatteryOverheatRule(60);

        assertThat(rule.evaluate(reading(T0, 59.9, false))).isEmpty();
        assertThat(rule.evaluate(reading(T0, 60, false))).isPresent();
    }

    @Test
    void anomaliaRegistadaPorOutraReplicaNaoAbreSegundaOrdem() {
        f.repository.registerIfNew(new ActiveAnomalyEntity(BIKE, AnomalyType.BRAKE_FAULT.name(), T0));
        AnomalyRecorder recorder = new AnomalyRecorder(f.repository, f.maintenance);

        recorder.record(BIKE, T0, List.of(new Anomaly(AnomalyType.BRAKE_FAULT, "travões")), List.of());

        assertThat(f.maintenance.opened).isEmpty();
    }
}
