package pt.lisboapedala.trip_management.port;

/**
 * Resultado da aplicação de um evento de doca a uma viagem.
 * O dock_gateway responde 2xx à doca em todos os casos (ARCHITECTURE.md §7.3, notas).
 */
public enum TripEventOutcome {
    /** O evento mudou o estado da viagem. */
    APPLIED,
    /** O evento já tinha sido aplicado (mesmo eventId); nada mudou. */
    DUPLICATE,
    /** Nenhuma viagem corresponde ao evento. */
    UNMATCHED
}
