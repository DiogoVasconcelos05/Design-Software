package pt.lisboapedala.station_inventory.query;

import java.util.List;
import java.util.Objects;

/**
 * Visão imutável de ocupação de uma estação para consulta (FF-06, FF-18).
 * Não expõe dados pessoais nem percursos.
 */
public record StationOccupancy(
        String stationId,
        String name,
        String parish,
        double latitude,
        double longitude,
        int totalDocks,
        int availableBikes,
        int freeDocks,
        int outOfServiceDocks,
        List<DockStatusView> docks
) {
    public StationOccupancy {
        Objects.requireNonNull(stationId, "stationId não pode ser nulo");
        Objects.requireNonNull(name, "name não pode ser nulo");
        Objects.requireNonNull(parish, "parish não pode ser nula");
        Objects.requireNonNull(docks, "docks não pode ser nulo");
        docks = List.copyOf(docks);
    }
}