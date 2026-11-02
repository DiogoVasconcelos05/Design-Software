package pt.lisboapedala.api.rest;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.common.exception.EntityNotFoundException;
import pt.lisboapedala.station_inventory.query.StationOccupancy;
import pt.lisboapedala.station_inventory.query.StationOccupancyQuery;

/**
 * Endpoints 11 e 12 de ARCHITECTURE.md §7.3 (Req. 4): ocupação das estações, só por leitura
 * ({@code StationOccupancyQuery}, separada das escritas das viagens, §3 elasticidade).
 */
@RestController
@RequestMapping("/stations")
public class StationController {

    private final StationOccupancyQuery stationOccupancyQuery;

    public StationController(StationOccupancyQuery stationOccupancyQuery) {
        this.stationOccupancyQuery = stationOccupancyQuery;
    }

    /** 11. {@code GET /stations} → 200. */
    @GetMapping
    public List<StationOccupancy> findAll() {
        return stationOccupancyQuery.findAll();
    }

    /** 12. {@code GET /stations/{stationId}} → 200, ou 404. */
    @GetMapping("/{stationId}")
    public StationOccupancy findByStation(@PathVariable String stationId) {
        return stationOccupancyQuery.findByStationId(stationId)
                .orElseThrow(() -> new EntityNotFoundException("Estação", stationId));
    }
}
