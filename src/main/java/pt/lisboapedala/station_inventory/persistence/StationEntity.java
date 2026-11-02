package pt.lisboapedala.station_inventory.persistence;

public record StationEntity(
        String stationId,
        String name,
        String parish,
        double latitude,
        double longitude,
        boolean active
) {
}