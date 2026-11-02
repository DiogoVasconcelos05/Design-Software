package pt.lisboapedala.fleet_health.internal;

import pt.lisboapedala.maintenance_orders.port.FaultType;

/**
 * Anomalias que fleet_health sabe detetar, cada uma com o tipo de avaria da ordem de manutenção que abre.
 * O nome é guardado em {@code fleet_active_anomalies.anomaly_type}.
 */
public enum AnomalyType {
    BATTERY_OVERHEAT(FaultType.BATTERY),
    BRAKE_FAULT(FaultType.BRAKES);

    private final FaultType faultType;

    AnomalyType(FaultType faultType) {
        this.faultType = faultType;
    }

    public FaultType faultType() {
        return faultType;
    }
}
