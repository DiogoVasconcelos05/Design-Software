package pt.lisboapedala.fleet_health.internal;

import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pt.lisboapedala.fleet_health.port.TelemetryReading;

/**
 * Falha de bateria: a temperatura chegou a {@code lisboapedala.fleet.battery-max-temperature-celsius}.
 */
@Component
public class BatteryOverheatRule implements AnomalyRule {

    private final double maxTemperatureCelsius;

    public BatteryOverheatRule(
            @Value("${lisboapedala.fleet.battery-max-temperature-celsius}") double maxTemperatureCelsius) {
        this.maxTemperatureCelsius = maxTemperatureCelsius;
    }

    @Override
    public AnomalyType type() {
        return AnomalyType.BATTERY_OVERHEAT;
    }

    @Override
    public Optional<Anomaly> evaluate(TelemetryReading reading) {
        if (reading.batteryTemperatureCelsius() < maxTemperatureCelsius) {
            return Optional.empty();
        }
        return Optional.of(new Anomaly(type(), String.format(Locale.ROOT,
                "Temperatura da bateria de %.1f °C (máx. %.1f °C)",
                reading.batteryTemperatureCelsius(), maxTemperatureCelsius)));
    }
}
