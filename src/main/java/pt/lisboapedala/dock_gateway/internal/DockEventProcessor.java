package pt.lisboapedala.dock_gateway.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.dock_gateway.persistence.ProcessedEventEntity;
import pt.lisboapedala.dock_gateway.persistence.ProcessedEventRepository;
import pt.lisboapedala.dock_gateway.port.DockEventSubmission;
import pt.lisboapedala.dock_gateway.port.IngestOutcome;
import pt.lisboapedala.station_inventory.port.DockOccupancyPort;
import pt.lisboapedala.trip_management.port.TripEventOutcome;
import pt.lisboapedala.trip_management.port.TripService;

/**
 * Unidade de trabalho de um evento de doca (ARCHITECTURE.md §7.4.2): registo do eventId, estado da doca e
 * viagem numa única transação local. Se algo falhar, o eventId não fica registado e o reenvio da doca volta
 * a processá-lo. A cobrança acontece em trip_management depois do commit (ADR-004 §4, FF-15).
 */
@Component
public class DockEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(DockEventProcessor.class);

    private final ProcessedEventRepository processedEvents;
    private final DockOccupancyPort docks;
    private final TripService trips;
    private final Clock clock;

    public DockEventProcessor(ProcessedEventRepository processedEvents, DockOccupancyPort docks,
                              TripService trips, Clock clock) {
        this.processedEvents = processedEvents;
        this.docks = docks;
        this.trips = trips;
        this.clock = clock;
    }

    @Transactional
    public IngestOutcome process(DockEventSubmission submission) {
        DockEventRecord event = submission.event();
        // ADR-004 §2: primeira linha de defesa
        if (!processedEvents.registerIfNew(new ProcessedEventEntity(event.eventId(), event.dockId(),
                event.eventType(), event.occurredAt(), Instant.now(clock)))) {
            log.info("Evento {} da doca {} duplicado; descartado", event.eventId(), event.dockId());
            return IngestOutcome.DUPLICATE;
        }

        IngestOutcome outcome = switch (event.eventType()) {
            case BIKE_RELEASED -> released(event, submission.commandId());
            case BIKE_DOCKED -> docked(event);
            case DOCK_MAINTENANCE_LOCKED -> locked(event, true);
            case DOCK_MAINTENANCE_UNLOCKED -> locked(event, false);
        };
        log.info("Evento {} {} da doca {}: {}", event.eventId(), event.eventType(), event.dockId(), outcome);
        return outcome;
    }

    /** §7.4.1: a viagem passa a IN_PROGRESS e a doca fica livre (a bicicleta saiu, haja ou não viagem). */
    private IngestOutcome released(DockEventRecord event, UUID commandId) {
        IngestOutcome outcome = commandId == null
                ? IngestOutcome.UNMATCHED
                : toIngestOutcome(trips.startTrip(commandId, event));
        docks.markDockFree(event.dockId(), event.occurredAt());
        return outcome;
    }

    /** §7.4.2: o encaixe é registado antes do fecho, para ficarem consolidados antes da cobrança (§4). */
    private IngestOutcome docked(DockEventRecord event) {
        docks.markDockOccupied(event.dockId(), event.bikeId(), event.occurredAt());
        return toIngestOutcome(trips.closeTrip(event));
    }

    private IngestOutcome locked(DockEventRecord event, boolean locked) {
        docks.markDockLocked(event.dockId(), locked, event.occurredAt());
        return IngestOutcome.PROCESSED;
    }

    /**
     * Um DUPLICATE de trip_management (segunda linha de defesa, ADR-004 §3) significa que o evento já tinha
     * sido aplicado antes de o eventId ter sido purgado ou perdido aqui; para a doca é também um duplicado.
     */
    private static IngestOutcome toIngestOutcome(TripEventOutcome outcome) {
        return switch (outcome) {
            case APPLIED -> IngestOutcome.PROCESSED;
            case DUPLICATE -> IngestOutcome.DUPLICATE;
            case UNMATCHED -> IngestOutcome.UNMATCHED;
        };
    }
}
