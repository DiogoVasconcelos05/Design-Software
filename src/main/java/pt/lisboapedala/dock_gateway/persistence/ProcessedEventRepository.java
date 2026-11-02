package pt.lisboapedala.dock_gateway.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso à tabela {@code dock_processed_events}. Privado a dock_gateway.
 */
public interface ProcessedEventRepository {

    /**
     * Regista o eventId; a unicidade é garantida pela chave primária (ADR-004 §2).
     *
     * @return false se o eventId já estava registado (evento duplicado)
     */
    boolean registerIfNew(ProcessedEventEntity event);

    Optional<ProcessedEventEntity> findById(UUID eventId);

    /** Apaga os registos recebidos antes de {@code cutoff}; devolve quantos foram apagados. */
    int purgeReceivedBefore(Instant cutoff);
}
