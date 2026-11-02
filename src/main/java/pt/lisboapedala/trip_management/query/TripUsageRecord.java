package pt.lisboapedala.trip_management.query;

import java.time.Instant;
import java.util.UUID;

/**
 * Dados de utilização de uma viagem fechada para o relatório municipal (ADR-005 §3).
 * Não inclui identificação do utilizador nem o percurso (RGPD).
 */
public record TripUsageRecord(
        UUID tripId,
        String originDockId,
        String destinationDockId,
        String bikeId,
        Instant startedAt,
        Instant endedAt
) {
}
