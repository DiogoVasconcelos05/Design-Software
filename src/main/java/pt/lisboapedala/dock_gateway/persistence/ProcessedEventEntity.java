package pt.lisboapedala.dock_gateway.persistence;

import java.time.Instant;
import java.util.UUID;
import pt.lisboapedala.common.event.DockEventType;

/**
 * Linha da tabela {@code dock_processed_events}. Privada a dock_gateway.
 *
 * @param receivedAt Hora de receção no servidor; só serve para a purga, nunca para durações (ADR-004 §5).
 */
public record ProcessedEventEntity(
        UUID eventId,
        String dockId,
        DockEventType eventType,
        Instant occurredAt,
        Instant receivedAt
) {
}
