package pt.lisboapedala.fleet_health.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementação de {@link ActiveAnomalyRepository} com {@link JdbcClient} e SQL à mão (ARCHITECTURE.md §7.1).
 */
@Repository
public class JdbcActiveAnomalyRepository implements ActiveAnomalyRepository {

    private final JdbcClient jdbc;

    public JdbcActiveAnomalyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ActiveAnomalyEntity> findByBike(String bikeId) {
        return jdbc.sql("SELECT bike_id, anomaly_type, detected_at FROM fleet_active_anomalies WHERE bike_id = :bikeId")
                .param("bikeId", bikeId)
                .query(JdbcActiveAnomalyRepository::map)
                .list();
    }

    /** {@code INSERT OR IGNORE}: uma anomalia já ativa não lança exceção nem aborta a transação, só não insere. */
    @Override
    public boolean registerIfNew(ActiveAnomalyEntity anomaly) {
        return jdbc.sql("INSERT OR IGNORE INTO fleet_active_anomalies (bike_id, anomaly_type, detected_at) "
                        + "VALUES (:bikeId, :anomalyType, :detectedAt)")
                .param("bikeId", anomaly.bikeId())
                .param("anomalyType", anomaly.anomalyType())
                .param("detectedAt", anomaly.detectedAt().toEpochMilli())
                .update() == 1;
    }

    @Override
    public boolean clearIfDetectedBefore(String bikeId, String anomalyType, Instant resolvedAt) {
        return jdbc.sql("DELETE FROM fleet_active_anomalies "
                        + "WHERE bike_id = :bikeId AND anomaly_type = :anomalyType AND detected_at < :resolvedAt")
                .param("bikeId", bikeId)
                .param("anomalyType", anomalyType)
                .param("resolvedAt", resolvedAt.toEpochMilli())
                .update() == 1;
    }

    private static ActiveAnomalyEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new ActiveAnomalyEntity(
                rs.getString("bike_id"),
                rs.getString("anomaly_type"),
                Instant.ofEpochMilli(rs.getLong("detected_at")));
    }
}
