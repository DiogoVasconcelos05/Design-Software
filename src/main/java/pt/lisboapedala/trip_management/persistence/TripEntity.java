package pt.lisboapedala.trip_management.persistence;

import java.time.Instant;
import java.util.UUID;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.trip_management.port.TripStatus;

/**
 * Linha da tabela {@code trips}. Privada a trip_management (FF-04b: só usada por {@code internal}).
 */
public record TripEntity(
        UUID tripId,
        String userId,
        PassType passType,
        TripStatus status,
        String originDockId,
        String destinationDockId,
        String bikeId,
        Instant requestedAt,
        Instant startedAt,
        Instant endedAt,
        UUID startEventId,
        UUID closeEventId,
        boolean billingDispatched
) {
}
