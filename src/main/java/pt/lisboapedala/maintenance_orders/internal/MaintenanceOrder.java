package pt.lisboapedala.maintenance_orders.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.maintenance_orders.persistence.MaintenanceOrderEntity;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderView;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

/**
 * Ordem de manutenção: máquina de estados imutável ({@code OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED}).
 * Cada transição devolve uma nova instância; repetir uma transição já aplicada devolve a mesma instância
 * (sem efeitos) e uma transição fora de ordem dá {@code CONFLICT}. Os instantes vêm do relógio injetado.
 */
final class MaintenanceOrder {

    private final MaintenanceOrderEntity state;

    private MaintenanceOrder(MaintenanceOrderEntity state) {
        this.state = Objects.requireNonNull(state);
    }

    /** Nova ordem em OPEN, ainda sem técnico. */
    static MaintenanceOrder open(UUID id, OpenMaintenanceOrder command, Instant openedAt) {
        return new MaintenanceOrder(new MaintenanceOrderEntity(id, command.bikeId(), command.faultType(),
                command.origin(), MaintenanceOrderStatus.OPEN, command.description(), command.reportedBy(), null,
                null, Objects.requireNonNull(openedAt), null, null));
    }

    /** Atribui a um técnico; uma ordem ainda não iniciada pode ser reatribuída. */
    MaintenanceOrder assign(String technicianId) {
        Objects.requireNonNull(technicianId);
        if (state.status() == MaintenanceOrderStatus.ASSIGNED && technicianId.equals(state.technicianId())) {
            return this;
        }
        requireStatus(MaintenanceOrderStatus.OPEN, MaintenanceOrderStatus.ASSIGNED);
        return with(MaintenanceOrderStatus.ASSIGNED, technicianId, null, null, null);
    }

    /** O técnico atribuído começa a reparação. */
    MaintenanceOrder start(Instant startedAt) {
        if (state.status() == MaintenanceOrderStatus.IN_PROGRESS) {
            return this;
        }
        requireStatus(MaintenanceOrderStatus.ASSIGNED);
        return with(MaintenanceOrderStatus.IN_PROGRESS, state.technicianId(), null,
                Objects.requireNonNull(startedAt), null);
    }

    /** Conclui a reparação; a partir daqui a bicicleta pode voltar a ter uma ordem ativa deste tipo. */
    MaintenanceOrder complete(String notes, Instant completedAt) {
        if (state.status() == MaintenanceOrderStatus.COMPLETED) {
            return this;
        }
        requireStatus(MaintenanceOrderStatus.IN_PROGRESS);
        return with(MaintenanceOrderStatus.COMPLETED, state.technicianId(), notes, state.startedAt(),
                Objects.requireNonNull(completedAt));
    }

    UUID id() {
        return state.orderId();
    }

    MaintenanceOrderStatus status() {
        return state.status();
    }

    MaintenanceOrderView toView() {
        return new MaintenanceOrderView(state.orderId(), state.bikeId(), state.faultType(), state.origin(),
                state.status(), state.description(), state.reportedBy(), state.technicianId(), state.openedAt(),
                state.startedAt(), state.completedAt(), state.notes());
    }

    MaintenanceOrderEntity toEntity() {
        return state;
    }

    static MaintenanceOrder fromEntity(MaintenanceOrderEntity entity) {
        return new MaintenanceOrder(entity);
    }

    private MaintenanceOrder with(MaintenanceOrderStatus status, String technicianId, String notes,
                                  Instant startedAt, Instant completedAt) {
        return new MaintenanceOrder(new MaintenanceOrderEntity(state.orderId(), state.bikeId(), state.faultType(),
                state.origin(), status, state.description(), state.reportedBy(), technicianId, notes,
                state.openedAt(), startedAt, completedAt));
    }

    private void requireStatus(MaintenanceOrderStatus... allowed) {
        for (MaintenanceOrderStatus s : allowed) {
            if (state.status() == s) {
                return;
            }
        }
        throw new BusinessRuleException(ErrorCode.CONFLICT,
                "A ordem de manutenção " + state.orderId() + " está em " + state.status()
                        + " e não permite esta operação.");
    }
}
