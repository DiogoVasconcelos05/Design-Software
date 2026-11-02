package pt.lisboapedala.maintenance_orders.port;

import java.util.List;
import java.util.UUID;

/**
 * Porta de escrita publicada por maintenance_orders (ARCHITECTURE.md §7.3, endpoints 13 a 17, e §7.4.3).
 * Usada por fleet_health ({@code AUTOMATIC}) e pela api ({@code USER_REPORT} e ciclo de vida da ordem); a
 * implementação é privada a maintenance_orders.
 *
 * <p>Ciclo de vida: {@code OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED}. Repetir uma transição já aplicada
 * devolve a ordem sem efeitos novos; uma transição fora de ordem dá {@code CONFLICT} e uma ordem inexistente
 * dá {@code NOT_FOUND}.
 */
public interface MaintenanceService {

    /**
     * Abre uma ordem de manutenção. Se já existir uma ordem ativa para {@code (bikeId, faultType)}, não cria
     * outra e devolve a existente (§7.4.3), por isso repetir o pedido é seguro.
     */
    MaintenanceOrderView open(OpenMaintenanceOrder command);

    /**
     * Ordens com o estado pedido, da mais antiga para a mais recente (endpoint 14). Sem estado ({@code null}),
     * devolve as ordens ativas (todas menos {@code COMPLETED}).
     */
    List<MaintenanceOrderView> list(MaintenanceOrderStatus status);

    /** Atribui a ordem a um técnico (endpoint 15). Uma ordem ainda não iniciada pode ser reatribuída. */
    MaintenanceOrderView assign(UUID orderId, String technicianId);

    /** O técnico atribuído começa a reparação (endpoint 16). */
    MaintenanceOrderView start(UUID orderId);

    /** Conclui a reparação com as notas do técnico (endpoint 17); a bicicleta deixa de ter ordem ativa. */
    MaintenanceOrderView complete(UUID orderId, String notes);
}
