package pt.lisboapedala.common.event;

/**
 * Tipos de eventos emitidos pelas docas de bicicletas.
 */
public enum DockEventType {
    BIKE_RELEASED,
    BIKE_DOCKED,
    DOCK_MAINTENANCE_LOCKED,
    DOCK_MAINTENANCE_UNLOCKED
}