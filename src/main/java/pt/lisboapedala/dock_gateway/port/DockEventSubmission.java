package pt.lisboapedala.dock_gateway.port;

import java.util.Objects;
import java.util.UUID;
import pt.lisboapedala.common.event.DockEventRecord;

/**
 * Evento recebido de uma doca, tal como chega à api.
 *
 * @param event     Evento imutável da doca (eventId e occurredAt gerados na origem, ADR-004 §1).
 * @param commandId Identificador do comando de abertura (o tripId) a que o evento responde; só existe em
 *                  BIKE_RELEASED e pode ser null nos restantes tipos.
 */
public record DockEventSubmission(DockEventRecord event, UUID commandId) {

    public DockEventSubmission {
        Objects.requireNonNull(event, "event não pode ser nulo");
    }
}
