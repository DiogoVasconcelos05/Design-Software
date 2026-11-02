package pt.lisboapedala.maintenance_orders.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.maintenance_orders.internal.MaintenanceTestFixture.BIKE;
import static pt.lisboapedala.maintenance_orders.internal.MaintenanceTestFixture.T0;
import static pt.lisboapedala.maintenance_orders.internal.MaintenanceTestFixture.TECHNICIAN;
import static pt.lisboapedala.maintenance_orders.internal.MaintenanceTestFixture.automatic;
import static pt.lisboapedala.maintenance_orders.internal.MaintenanceTestFixture.userReport;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.lisboapedala.common.exception.DomainException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderView;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

class DefaultMaintenanceServiceTest {

    @TempDir
    Path dir;
    MaintenanceTestFixture f;

    @BeforeEach
    void setUp() {
        f = new MaintenanceTestFixture(dir);
    }

    @Test
    void abreOrdemEmOpenComAHoraDoRelogio() {
        MaintenanceOrderView order = f.service.open(userReport(FaultType.BRAKES));

        assertThat(order.status()).isEqualTo(MaintenanceOrderStatus.OPEN);
        assertThat(order.bikeId()).isEqualTo(BIKE);
        assertThat(order.origin()).isEqualTo(MaintenanceOrigin.USER_REPORT);
        assertThat(order.reportedBy()).isEqualTo("user-42");
        assertThat(order.technicianId()).isNull();
        assertThat(order.openedAt()).isEqualTo(T0);
        assertThat(f.repository.findById(order.orderId())).isPresent();
    }

    @Test
    void naoCriaSegundaOrdemAtivaParaAMesmaBicicletaEAvaria() {
        MaintenanceOrderView first = f.service.open(automatic(FaultType.BATTERY));
        MaintenanceOrderView second = f.service.open(userReport(FaultType.BATTERY));

        assertThat(second).isEqualTo(first);
        assertThat(f.service.list(null)).hasSize(1);
    }

    @Test
    void avariasDiferentesDaMesmaBicicletaTemOrdensSeparadas() {
        MaintenanceOrderView battery = f.service.open(automatic(FaultType.BATTERY));
        MaintenanceOrderView brakes = f.service.open(automatic(FaultType.BRAKES));

        assertThat(brakes.orderId()).isNotEqualTo(battery.orderId());
    }

    @Test
    void cicloDeVidaCompletoGuardaTecnicoInstantesENotas() {
        UUID id = f.service.open(userReport(FaultType.BRAKES)).orderId();

        assertThat(f.service.assign(id, TECHNICIAN).status()).isEqualTo(MaintenanceOrderStatus.ASSIGNED);
        f.clock.advance(Duration.ofHours(1));
        MaintenanceOrderView started = f.service.start(id);
        f.clock.advance(Duration.ofMinutes(30));
        MaintenanceOrderView completed = f.service.complete(id, "Pastilhas substituídas");

        assertThat(started.status()).isEqualTo(MaintenanceOrderStatus.IN_PROGRESS);
        assertThat(completed.status()).isEqualTo(MaintenanceOrderStatus.COMPLETED);
        assertThat(completed.technicianId()).isEqualTo(TECHNICIAN);
        assertThat(completed.startedAt()).isEqualTo(T0.plus(Duration.ofHours(1)));
        assertThat(completed.completedAt()).isEqualTo(T0.plus(Duration.ofMinutes(90)));
        assertThat(completed.notes()).isEqualTo("Pastilhas substituídas");
        assertThat(f.repository.findById(id)).hasValueSatisfying(e ->
                assertThat(e.status()).isEqualTo(MaintenanceOrderStatus.COMPLETED));
    }

    @Test
    void depoisDeConcluidaABicicletaPodeTerNovaOrdemDoMesmoTipo() {
        UUID first = f.service.open(automatic(FaultType.BATTERY)).orderId();
        f.service.assign(first, TECHNICIAN);
        f.service.start(first);
        f.service.complete(first, null);

        MaintenanceOrderView again = f.service.open(automatic(FaultType.BATTERY));

        assertThat(again.orderId()).isNotEqualTo(first);
        assertThat(again.status()).isEqualTo(MaintenanceOrderStatus.OPEN);
    }

    @Test
    void repetirUmaTransicaoJaAplicadaNaoTemEfeitos() {
        UUID id = f.service.open(userReport(FaultType.BRAKES)).orderId();
        MaintenanceOrderView assigned = f.service.assign(id, TECHNICIAN);
        assertThat(f.service.assign(id, TECHNICIAN)).isEqualTo(assigned);
        MaintenanceOrderView started = f.service.start(id);
        f.clock.advance(Duration.ofMinutes(5));

        assertThat(f.service.start(id)).isEqualTo(started);
        MaintenanceOrderView completed = f.service.complete(id, "ok");
        f.clock.advance(Duration.ofMinutes(5));
        assertThat(f.service.complete(id, "outra nota")).isEqualTo(completed);
    }

    @Test
    void ordemNaoIniciadaPodeSerReatribuida() {
        UUID id = f.service.open(userReport(FaultType.OTHER)).orderId();
        f.service.assign(id, TECHNICIAN);

        assertThat(f.service.assign(id, "tec-9").technicianId()).isEqualTo("tec-9");
    }

    @Test
    void transicoesForaDeOrdemDaoConflict() {
        UUID id = f.service.open(userReport(FaultType.BRAKES)).orderId();

        assertErrorCode(() -> f.service.start(id), ErrorCode.CONFLICT);
        assertErrorCode(() -> f.service.complete(id, "x"), ErrorCode.CONFLICT);
        f.service.assign(id, TECHNICIAN);
        f.service.start(id);
        assertErrorCode(() -> f.service.assign(id, "tec-9"), ErrorCode.CONFLICT);
    }

    @Test
    void ordemInexistenteDaNotFound() {
        assertErrorCode(() -> f.service.start(UUID.randomUUID()), ErrorCode.NOT_FOUND);
    }

    @Test
    void pedidosInvalidosDaoValidation() {
        UUID id = f.service.open(userReport(FaultType.BRAKES)).orderId();

        assertErrorCode(() -> f.service.open(new OpenMaintenanceOrder(" ", FaultType.BRAKES, "x", "user-42",
                MaintenanceOrigin.USER_REPORT)), ErrorCode.VALIDATION);
        assertErrorCode(() -> f.service.open(new OpenMaintenanceOrder(BIKE, FaultType.OTHER, "x", null,
                MaintenanceOrigin.USER_REPORT)), ErrorCode.VALIDATION);
        assertErrorCode(() -> f.service.open(null), ErrorCode.VALIDATION);
        assertErrorCode(() -> f.service.assign(id, ""), ErrorCode.VALIDATION);
        assertErrorCode(() -> f.service.start(null), ErrorCode.VALIDATION);
    }

    @Test
    void listaFiltraPorEstadoEPorOmissaoDevolveAsAtivasDaMaisAntiga() {
        UUID battery = f.service.open(automatic(FaultType.BATTERY)).orderId();
        f.clock.advance(Duration.ofMinutes(1));
        UUID brakes = f.service.open(automatic(FaultType.BRAKES)).orderId();
        f.clock.advance(Duration.ofMinutes(1));
        UUID other = f.service.open(userReport(FaultType.OTHER)).orderId();
        f.service.assign(brakes, TECHNICIAN);
        f.service.assign(other, TECHNICIAN);
        f.service.start(other);
        f.service.complete(other, "ok");

        assertThat(f.service.list(MaintenanceOrderStatus.OPEN)).extracting(MaintenanceOrderView::orderId)
                .containsExactly(battery);
        assertThat(f.service.list(MaintenanceOrderStatus.COMPLETED)).extracting(MaintenanceOrderView::orderId)
                .containsExactly(other);
        assertThat(f.service.list(null)).extracting(MaintenanceOrderView::orderId)
                .containsExactly(battery, brakes);
    }

    @Test
    void transicaoConcorrenteNaoSobrepoeOutra() {
        UUID id = f.service.open(userReport(FaultType.BRAKES)).orderId();
        MaintenanceOrder stale = MaintenanceOrder.fromEntity(f.repository.findById(id).orElseThrow());
        f.service.assign(id, TECHNICIAN);
        f.service.start(id);

        // Uma réplica que leu a ordem ainda em OPEN não consegue gravar por cima do início da reparação.
        assertThat(f.repository.update(stale.assign("tec-9").toEntity(), MaintenanceOrderStatus.OPEN)).isFalse();
        assertThat(f.repository.findById(id)).hasValueSatisfying(e -> {
            assertThat(e.status()).isEqualTo(MaintenanceOrderStatus.IN_PROGRESS);
            assertThat(e.technicianId()).isEqualTo(TECHNICIAN);
        });
    }

    private static void assertErrorCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(code);
    }
}
