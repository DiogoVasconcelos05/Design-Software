package pt.lisboapedala.fleet_health.internal;

import java.util.Optional;
import pt.lisboapedala.fleet_health.port.TelemetryReading;

/**
 * Regra de deteção de uma anomalia. Cada regra é um bean: acrescentar uma deteção é acrescentar uma regra,
 * sem mexer em {@link TelemetryService}. Tem de ser uma função pura da leitura (sem estado nem I/O).
 */
public interface AnomalyRule {

    /** O tipo de anomalia que esta regra deteta; uma regra por tipo. */
    AnomalyType type();

    /** A anomalia, se a leitura a revelar. */
    Optional<Anomaly> evaluate(TelemetryReading reading);
}
