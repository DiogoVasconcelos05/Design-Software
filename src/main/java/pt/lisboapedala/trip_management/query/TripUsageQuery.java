package pt.lisboapedala.trip_management.query;

import java.time.Instant;
import java.util.List;

/**
 * Consulta só de leitura publicada por trip_management para municipal_reporting (ADR-005 §3).
 */
public interface TripUsageQuery {

    /**
     * Viagens fechadas com fim em {@code [from, to[}, ordenadas por fim e paginadas.
     */
    List<TripUsageRecord> findClosedBetween(Instant from, Instant to, int limit, int offset);
}
