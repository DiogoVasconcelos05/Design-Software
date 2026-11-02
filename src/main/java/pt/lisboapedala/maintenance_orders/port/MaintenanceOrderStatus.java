package pt.lisboapedala.maintenance_orders.port;

/**
 * Estado de uma ordem de manutenção, pelas transições dos endpoints 15 a 17 de ARCHITECTURE.md §7.3.
 * Uma ordem está ativa enquanto não estiver {@code COMPLETED}.
 */
public enum MaintenanceOrderStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED
}
