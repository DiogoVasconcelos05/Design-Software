package pt.lisboapedala.common.valueobject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato imutável de uma viagem terminada, enviado por trip_management a payment_invoicing.
 * O tripId é a chave de idempotência da cobrança (ADR-004 §4).
 *
 * @param tripId          Identificador da viagem.
 * @param userId          Identificador do utilizador a cobrar.
 * @param passType        Tipo de passe do utilizador no momento do desbloqueio.
 * @param startedAt       Instante de início (occurredAt do evento BIKE_RELEASED).
 * @param endedAt         Instante de fim (occurredAt do evento BIKE_DOCKED).
 * @param durationMinutes Duração em minutos, arredondada para cima.
 */
public record TripSummary(
        UUID tripId,
        String userId,
        PassType passType,
        Instant startedAt,
        Instant endedAt,
        long durationMinutes
) {
    public TripSummary {
        Objects.requireNonNull(tripId, "tripId não pode ser nulo");
        Objects.requireNonNull(userId, "userId não pode ser nulo");
        Objects.requireNonNull(passType, "passType não pode ser nulo");
        Objects.requireNonNull(startedAt, "startedAt não pode ser nulo");
        Objects.requireNonNull(endedAt, "endedAt não pode ser nulo");

        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId não pode estar em branco");
        }
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes não pode ser negativo");
        }
    }
}
