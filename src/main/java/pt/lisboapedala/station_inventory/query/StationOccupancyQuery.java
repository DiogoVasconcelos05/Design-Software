package pt.lisboapedala.station_inventory.query;

import java.util.List;
import java.util.Optional;

/**
 * Interface pública de consulta do estado e localização de estações (FF-05, FF-06, ADR-005).
 * Nenhum método tem retorno void.
 */
public interface StationOccupancyQuery {

    List<StationOccupancy> findAll();

    Optional<StationOccupancy> findByStationId(String stationId);

    List<StationLocationRecord> findAllLocations();
}