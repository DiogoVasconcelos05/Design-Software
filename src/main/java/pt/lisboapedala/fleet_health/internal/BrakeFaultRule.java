package pt.lisboapedala.fleet_health.internal;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pt.lisboapedala.fleet_health.port.TelemetryReading;

/**
 * Falha de travões: o autodiagnóstico da bicicleta reportou-a na leitura.
 */
@Component
public class BrakeFaultRule implements AnomalyRule {

    @Override
    public AnomalyType type() {
        return AnomalyType.BRAKE_FAULT;
    }

    @Override
    public Optional<Anomaly> evaluate(TelemetryReading reading) {
        return reading.brakeFault()
                ? Optional.of(new Anomaly(type(), "Falha nos travões reportada pelo autodiagnóstico da bicicleta"))
                : Optional.empty();
    }
}
