package pt.lisboapedala.api.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.dock_gateway.port.DockEventIngestion;
import pt.lisboapedala.dock_gateway.port.DockEventSubmission;
import pt.lisboapedala.dock_gateway.port.IngestOutcome;

/**
 * Entrada dos eventos das docas ({@code POST /dock-events*}, ARCHITECTURE.md §7.3 notas, §7.4.1–§7.4.2, US-3).
 * Responde 200 em todos os resultados, também {@code DUPLICATE} e {@code UNMATCHED}: a doca só descarta o
 * evento do buffer local depois de um 2xx. Uma resposta de erro significa que o evento deve ser reenviado.
 */
@RestController
@RequestMapping("/dock-events")
public class DockEventController {

    private final DockEventIngestion ingestion;

    public DockEventController(DockEventIngestion ingestion) {
        this.ingestion = ingestion;
    }

    /** Um evento acabado de acontecer na doca. */
    @PostMapping
    public DockEventResponse ingest(@Valid @RequestBody DockEventRequest body) {
        return new DockEventResponse(body.eventId(), ingestion.ingest(body.toSubmission()));
    }

    /** Eventos guardados pela doca enquanto esteve offline (US-3); um resultado por evento, na ordem recebida. */
    @PostMapping("/batch")
    public List<DockEventResponse> ingestBatch(@RequestBody List<@Valid @NotNull DockEventRequest> body) {
        List<IngestOutcome> outcomes = ingestion.ingestBatch(body.stream().map(DockEventRequest::toSubmission).toList());
        return IntStream.range(0, body.size())
                .mapToObj(i -> new DockEventResponse(body.get(i).eventId(), outcomes.get(i)))
                .toList();
    }

    /**
     * Evento tal como a doca o envia. {@code eventId} e {@code occurredAt} são gerados na doca e mantêm-se nos
     * reenvios (ADR-004 §1); {@code commandId} (o tripId do comando de abertura) só vem em {@code BIKE_RELEASED}.
     */
    public record DockEventRequest(
            @NotNull UUID eventId,
            @NotBlank String dockId,
            @NotBlank String bikeId,
            @NotNull DockEventType eventType,
            @NotNull Instant occurredAt,
            UUID commandId
    ) {
        DockEventSubmission toSubmission() {
            return new DockEventSubmission(new DockEventRecord(eventId, dockId, bikeId, eventType, occurredAt), commandId);
        }
    }

    /** Resultado devolvido à doca para cada evento. */
    public record DockEventResponse(UUID eventId, IngestOutcome outcome) {
    }
}
