package pt.lisboapedala.maintenance_orders.port;

/**
 * Porta de escrita publicada por maintenance_orders (ARCHITECTURE.md §7.3, endpoint 13, e §7.4.3).
 * Usada por fleet_health ({@code AUTOMATIC}) e pela api ({@code USER_REPORT}); a implementação é privada
 * a maintenance_orders.
 */
public interface MaintenanceService {

    /**
     * Abre uma ordem de manutenção. Se já existir uma ordem ativa para {@code (bikeId, faultType)}, não cria
     * outra e devolve a existente (§7.4.3), por isso repetir o pedido é seguro.
     */
    MaintenanceOrderView open(OpenMaintenanceOrder command);
}
