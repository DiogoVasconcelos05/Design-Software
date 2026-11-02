package pt.lisboapedala.maintenance_orders.port;

/**
 * Canal pelo qual a avaria chegou (US-2, ARCHITECTURE.md §7.4.3).
 */
public enum MaintenanceOrigin {
    /** Anomalia detetada por fleet_health na telemetria. */
    AUTOMATIC,
    /** Reporte manual por {@code POST /maintenance-orders}. */
    USER_REPORT
}
