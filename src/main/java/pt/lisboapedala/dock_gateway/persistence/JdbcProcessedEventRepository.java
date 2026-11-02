package pt.lisboapedala.dock_gateway.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.lisboapedala.common.event.DockEventType;

/**
 * Implementação de {@link ProcessedEventRepository} com {@link JdbcClient} e SQL à mão (ARCHITECTURE.md §7.1).
 */
@Repository
public class JdbcProcessedEventRepository implements ProcessedEventRepository {

    private static final String COLUMNS = "event_id, dock_id, event_type, occurred_at, received_at";

    private final JdbcClient jdbc;

    public JdbcProcessedEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code INSERT OR IGNORE}: um duplicado não lança exceção nem aborta a transação, só não insere. */
    @Override
    public boolean registerIfNew(ProcessedEventEntity event) {
        return jdbc.sql("INSERT OR IGNORE INTO dock_processed_events (" + COLUMNS + ") "
                        + "VALUES (:eventId, :dockId, :eventType, :occurredAt, :receivedAt)")
                .param("eventId", event.eventId().toString())
                .param("dockId", event.dockId())
                .param("eventType", event.eventType().name())
                .param("occurredAt", event.occurredAt().toEpochMilli())
                .param("receivedAt", event.receivedAt().toEpochMilli())
                .update() == 1;
    }

    @Override
    public Optional<ProcessedEventEntity> findById(UUID eventId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dock_processed_events WHERE event_id = :id")
                .param("id", eventId.toString())
                .query(JdbcProcessedEventRepository::map)
                .optional();
    }

    @Override
    public int purgeReceivedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM dock_processed_events WHERE received_at < :cutoff")
                .param("cutoff", cutoff.toEpochMilli())
                .update();
    }

    private static ProcessedEventEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new ProcessedEventEntity(
                UUID.fromString(rs.getString("event_id")),
                rs.getString("dock_id"),
                DockEventType.valueOf(rs.getString("event_type")),
                Instant.ofEpochMilli(rs.getLong("occurred_at")),
                Instant.ofEpochMilli(rs.getLong("received_at")));
    }
}
