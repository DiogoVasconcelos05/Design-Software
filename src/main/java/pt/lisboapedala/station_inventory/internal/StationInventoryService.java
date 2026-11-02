package pt.lisboapedala.station_inventory.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.common.exception.EntityNotFoundException;
import pt.lisboapedala.station_inventory.persistence.DockEntity;
import pt.lisboapedala.station_inventory.persistence.StationEntity;
import pt.lisboapedala.station_inventory.persistence.StationInventoryRepository;
import pt.lisboapedala.station_inventory.port.DockOccupancyPort;
import pt.lisboapedala.station_inventory.query.DockStatusView;
import pt.lisboapedala.station_inventory.query.StationLocationRecord;
import pt.lisboapedala.station_inventory.query.StationOccupancy;
import pt.lisboapedala.station_inventory.query.StationOccupancyQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação interna das interfaces de consulta e porta de alteração de estado (FF-11, FF-16, FF-19).
 * Suporta tolerância a reenvios fora de ordem verificando se o ocorreuAt é mais recente que o registo atual.
 */
@Service
public class StationInventoryService implements StationOccupancyQuery, DockOccupancyPort {

    private static final Logger log = LoggerFactory.getLogger(StationInventoryService.class);

    private static final String STATUS_OCCUPIED = "OCCUPIED";
    private static final String STATUS_FREE = "FREE";
    private static final String STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE";

    private final StationInventoryRepository repository;

    public StationInventoryService(StationInventoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository não pode ser nulo");
    }

    @Override
    public List<StationOccupancy> findAll() {
        List<StationEntity> stations = repository.findAllStations();
        List<StationOccupancy> result = new ArrayList<>();
        for (StationEntity station : stations) {
            result.add(mapToOccupancy(station));
        }
        return result;
    }

    @Override
    public Optional<StationOccupancy> findByStationId(String stationId) {
        return repository.findStationById(stationId)
                .map(this::mapToOccupancy);
    }

    @Override
    public List<StationLocationRecord> findAllLocations() {
        return repository.findAllStations().stream()
                .map(s -> new StationLocationRecord(s.stationId(), s.name(), s.parish(), s.latitude(), s.longitude()))
                .toList();
    }

    @Override
    @Transactional
    public void markDockFree(String dockId, Instant occurredAt) {
        log.info("A marcar doca como livre: dockId={}, occurredAt={}", dockId, occurredAt);
        DockEntity dock = findDockOrThrow(dockId);
        if (isOutdated(dock, occurredAt)) {
            log.warn("Evento ignorado por ser mais antigo que a última atualização da doca: dockId={}", dockId);
            return;
        }
        repository.updateDockState(dockId, null, STATUS_FREE, occurredAt);
    }

    @Override
    @Transactional
    public void markDockOccupied(String dockId, String bikeId, Instant occurredAt) {
        log.info("A marcar doca como ocupada: dockId={}, bikeId={}, occurredAt={}", dockId, bikeId, occurredAt);
        DockEntity dock = findDockOrThrow(dockId);
        if (isOutdated(dock, occurredAt)) {
            log.warn("Evento ignorado por ser mais antigo que a última atualização da doca: dockId={}", dockId);
            return;
        }
        repository.updateDockState(dockId, bikeId, STATUS_OCCUPIED, occurredAt);
    }

    @Override
    @Transactional
    public void markDockLocked(String dockId, boolean locked, Instant occurredAt) {
        log.info("A alterar estado de bloqueio da doca: dockId={}, locked={}, occurredAt={}", dockId, locked, occurredAt);
        DockEntity dock = findDockOrThrow(dockId);
        if (isOutdated(dock, occurredAt)) {
            log.warn("Evento ignorado por ser mais antigo que a última atualização da doca: dockId={}", dockId);
            return;
        }

        if (locked) {
            repository.updateDockState(dockId, dock.bikeId(), STATUS_OUT_OF_SERVICE, occurredAt);
        } else {
            String targetStatus = dock.bikeId() != null ? STATUS_OCCUPIED : STATUS_FREE;
            repository.updateDockState(dockId, dock.bikeId(), targetStatus, occurredAt);
        }
    }

    private DockEntity findDockOrThrow(String dockId) {
        return repository.findDockById(dockId)
                .orElseThrow(() -> new EntityNotFoundException("Doca", dockId));
    }

    private boolean isOutdated(DockEntity dock, Instant occurredAt) {
        return dock.updatedAt() != null && occurredAt.isBefore(dock.updatedAt());
    }

    private StationOccupancy mapToOccupancy(StationEntity station) {
        List<DockEntity> docks = repository.findDocksByStationId(station.stationId());

        int availableBikes = 0;
        int freeDocks = 0;
        int outOfService = 0;

        List<DockStatusView> dockViews = new ArrayList<>();
        for (DockEntity dock : docks) {
            dockViews.add(new DockStatusView(dock.dockId(), dock.bikeId(), dock.status()));
            switch (dock.status()) {
                case STATUS_OCCUPIED -> availableBikes++;
                case STATUS_FREE -> freeDocks++;
                case STATUS_OUT_OF_SERVICE -> outOfService++;
            }
        }

        return new StationOccupancy(
                station.stationId(),
                station.name(),
                station.parish(),
                station.latitude(),
                station.longitude(),
                docks.size(),
                availableBikes,
                freeDocks,
                outOfService,
                dockViews
        );
    }
}