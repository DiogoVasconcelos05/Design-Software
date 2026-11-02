package pt.lisboapedala.fleet_health.port;

import java.time.Instant;

/**
 * Leitura de telemetria enviada por uma bicicleta elétrica a cada 30 s (corpo do endpoint 10 de
 * ARCHITECTURE.md §7.3). A validação é feita por fleet_health, para devolver {@code VALIDATION}.
 *
 * @param bikeId                    Bicicleta que enviou a leitura.
 * @param occurredAt                Hora da bicicleta em que a leitura foi feita (nunca a hora de receção).
 * @param batteryTemperatureCelsius Temperatura da bateria, em graus Celsius.
 * @param brakeFault                true se o autodiagnóstico da bicicleta detetou uma falha nos travões.
 */
public record TelemetryReading(
        String bikeId,
        Instant occurredAt,
        double batteryTemperatureCelsius,
        boolean brakeFault
) {
}
