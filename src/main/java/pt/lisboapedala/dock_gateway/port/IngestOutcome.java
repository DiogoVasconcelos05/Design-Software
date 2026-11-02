package pt.lisboapedala.dock_gateway.port;

/**
 * Resultado do processamento de um evento de doca. Todos dão 200 à doca (ARCHITECTURE.md §7.3, notas).
 */
public enum IngestOutcome {
    /** O evento foi registado e aplicado. */
    PROCESSED,
    /** O eventId já tinha sido processado; nada mudou (ADR-004 §2). */
    DUPLICATE,
    /** O evento foi registado, mas nenhuma viagem lhe corresponde. */
    UNMATCHED
}
