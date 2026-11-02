package pt.lisboapedala.station_inventory.query;

import java.util.Objects;

/**
 * Record para suporte a consultas de localização/freguesia por outros módulos (ex.: municipal_reporting) - ADR-005.
 */
public record StationLocationRecord(
        String stationId,
        String name,
        String parish,
        double latitude,
        double longitude
) {
    public StationLocationRecord {
        Objects.requireNonNull(stationId, "stationId não pode ser nulo");
        Objects.requireNonNull(name, "name não pode ser nulo");
        Objects.requireNonNull(parish, "parish não pode ser nula");
    }
}