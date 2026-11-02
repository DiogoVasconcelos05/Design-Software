package pt.lisboapedala.trip_management.port;

import java.util.List;
import java.util.UUID;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.valueobject.Credential;

/**
 * Porta publicada por trip_management: ciclo de vida da viagem (US-1, US-3, Req. 8a).
 * Usada pela api (pedidos do utilizador) e pelo dock_gateway (eventos das docas).
 */
public interface TripService {

    /**
     * Valida a credencial, cria a viagem em REQUESTED e pede a abertura da doca.
     * Lança {@code PASS_INVALID} se o passe não for válido (nada é enviado à doca)
     * e {@code CONFLICT} se o utilizador já tiver uma viagem ativa.
     */
    TripView requestUnlock(Credential credential, String dockId);

    /** Lança {@code NOT_FOUND} se a viagem não existir. */
    TripView getTrip(UUID tripId);

    /** Histórico do utilizador, mais recente primeiro. */
    List<TripView> history(String userId, int limit, int offset);

    /**
     * Aplica um evento BIKE_RELEASED: a viagem {@code tripId} (o commandId do comando de abertura)
     * passa a IN_PROGRESS. Idempotente pelo {@code eventId}.
     */
    TripEventOutcome startTrip(UUID tripId, DockEventRecord event);

    /**
     * Aplica um evento BIKE_DOCKED: fecha a viagem em curso da bicicleta. Idempotente pelo
     * {@code eventId} (ADR-004 §3); junta-se à transação do chamador e a cobrança só é pedida após o commit.
     */
    TripEventOutcome closeTrip(DockEventRecord event);
}
