package pt.lisboapedala.trip_management.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista imutável de uma viagem devolvida à api (endpoints 4, 5 e 6 de ARCHITECTURE.md §7.3).
 * Os campos de início e fim ficam a null enquanto a viagem não os tiver.
 */
public record TripView(
        UUID tripId,
        String userId,
        TripStatus status,
        String originDockId,
        String destinationDockId,
        String bikeId,
        Instant requestedAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMinutes
) {
}
