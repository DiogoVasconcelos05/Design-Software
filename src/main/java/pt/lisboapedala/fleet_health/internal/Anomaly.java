package pt.lisboapedala.fleet_health.internal;

import java.util.Objects;

/**
 * Anomalia detetada numa leitura de telemetria.
 *
 * @param type        Tipo da anomalia.
 * @param description Descrição para o técnico, com os valores lidos.
 */
public record Anomaly(AnomalyType type, String description) {

    public Anomaly {
        Objects.requireNonNull(type, "type não pode ser nulo");
        Objects.requireNonNull(description, "description não pode ser nula");
    }
}
