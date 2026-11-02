package pt.lisboapedala.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento imutável emitido por uma doca de bicicleta.
 * Suporta a garantia de idempotência no fecho de viagens e processamento de eventos (ADR-004).
 *
 * @param eventId    Identificador único global do evento gerado na origem (doca).
 * @param dockId     Identificador da doca de onde o evento se originou.
 * @param bikeId     Identificador da bicicleta envolvida no evento.
 * @param eventType  Tipo do evento ocorrido na doca.
 * @param occurredAt Instante em que o evento ocorreu no relógio da doca (em UTC/ISO-8601).
 */
public record DockEventRecord(
        UUID eventId,
        String dockId,
        String bikeId,
        DockEventType eventType,
        Instant occurredAt
) {
    public DockEventRecord {
        Objects.requireNonNull(eventId, "eventId não pode ser nulo");
        Objects.requireNonNull(dockId, "dockId não pode ser nulo");
        Objects.requireNonNull(bikeId, "bikeId não pode ser nulo");
        Objects.requireNonNull(eventType, "eventType não pode ser nulo");
        Objects.requireNonNull(occurredAt, "occurredAt não pode ser nulo");

        if (dockId.isBlank()) {
            throw new IllegalArgumentException("dockId não pode estar em branco");
        }
        if (bikeId.isBlank()) {
            throw new IllegalArgumentException("bikeId não pode estar em branco");
        }
    }
}