package pt.lisboapedala.dock_gateway.port;

import java.util.List;

/**
 * Porta publicada por dock_gateway para a entrada de eventos das docas (POST /dock-events*, ARCHITECTURE.md §7.3).
 * Usada pela api. Qualquer resultado devolvido corresponde a 2xx: a doca só descarta o evento do buffer
 * local depois disso. Uma exceção significa que o evento não ficou registado e a doca deve reenviá-lo.
 */
public interface DockEventIngestion {

    /**
     * Processa um evento numa única transação local: regista o eventId (unicidade, ADR-004 §2),
     * atualiza station_inventory e aplica-o a trip_management.
     */
    IngestOutcome ingest(DockEventSubmission submission);

    /**
     * Processa os eventos guardados por uma doca que esteve offline (US-3), cada um na sua própria transação
     * e por ordem de {@code occurredAt}. Devolve um resultado por evento, na ordem recebida.
     */
    List<IngestOutcome> ingestBatch(List<DockEventSubmission> submissions);
}
