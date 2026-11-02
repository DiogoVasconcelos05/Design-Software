package pt.lisboapedala.station_inventory.persistence;

import java.time.Instant;

public record DockEntity(
        String dockId,
        String stationId,
        String bikeId,
        String status,
        Instant updatedAt
) {
}