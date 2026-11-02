package pt.lisboapedala.fleet_health.persistence;

import java.time.Instant;

/**
 * Linha da tabela {@code fleet_active_anomalies}. Privada a fleet_health.
 *
 * @param anomalyType Nome da anomalia detetada (ex.: {@code BATTERY_OVERHEAT}).
 * @param detectedAt  {@code occurredAt} da leitura que a detetou (hora da bicicleta).
 */
public record ActiveAnomalyEntity(
        String bikeId,
        String anomalyType,
        Instant detectedAt
) {
}
