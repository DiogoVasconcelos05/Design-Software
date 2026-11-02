package pt.lisboapedala.municipal_reporting.internal;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Record DTO que representa a métrica agregada por freguesia.
 *
 * @param parish            Nome da freguesia.
 * @param totalTrips        Total de viagens iniciadas nesta freguesia.
 * @param totalDistanceKm   Distância total percorrida em quilómetros.
 * @param estimatedCo2AvoidedKg Estimativa de CO2 evitado em quilogramas.
 */
public record ParishCo2Record(
        String parish,
        long totalTrips,
        BigDecimal totalDistanceKm,
        BigDecimal estimatedCo2AvoidedKg
) {
    public ParishCo2Record {
        Objects.requireNonNull(parish, "parish não pode ser nulo");
        Objects.requireNonNull(totalDistanceKm, "totalDistanceKm não pode ser nulo");
        Objects.requireNonNull(estimatedCo2AvoidedKg, "estimatedCo2AvoidedKg não pode ser nulo");

        if (totalTrips < 0) {
            throw new IllegalArgumentException("totalTrips não pode ser negativo");
        }
    }
}