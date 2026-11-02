package pt.lisboapedala.station_inventory.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repositório Spring JdbcClient privado do módulo (FF-04, FF-12).
 * Sem JPA. Acessível APENAS por internal/.
 */
@Repository
public class StationInventoryRepository {

    private final JdbcClient jdbcClient;

    public StationInventoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<StationEntity> findAllStations() {
        return jdbcClient.sql("SELECT station_id, name, parish, latitude, longitude, active FROM stations")
                .query((rs, rowNum) -> new StationEntity(
                        rs.getString("station_id"),
                        rs.getString("name"),
                        rs.getString("parish"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getBoolean("active")
                ))
                .list();
    }

    public Optional<StationEntity> findStationById(String stationId) {
        return jdbcClient.sql("SELECT station_id, name, parish, latitude, longitude, active FROM stations WHERE station_id = :stationId")
                .param("stationId", stationId)
                .query((rs, rowNum) -> new StationEntity(
                        rs.getString("station_id"),
                        rs.getString("name"),
                        rs.getString("parish"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getBoolean("active")
                ))
                .optional();
    }

    public List<DockEntity> findDocksByStationId(String stationId) {
        return jdbcClient.sql("SELECT dock_id, station_id, bike_id, status, updated_at FROM docks WHERE station_id = :stationId")
                .param("stationId", stationId)
                .query((rs, rowNum) -> new DockEntity(
                        rs.getString("dock_id"),
                        rs.getString("station_id"),
                        rs.getString("bike_id"),
                        rs.getString("status"),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .list();
    }

    public Optional<DockEntity> findDockById(String dockId) {
        return jdbcClient.sql("SELECT dock_id, station_id, bike_id, status, updated_at FROM docks WHERE dock_id = :dockId")
                .param("dockId", dockId)
                .query((rs, rowNum) -> new DockEntity(
                        rs.getString("dock_id"),
                        rs.getString("station_id"),
                        rs.getString("bike_id"),
                        rs.getString("status"),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
    }

    public int updateDockState(String dockId, String bikeId, String status, Instant updatedAt) {
        return jdbcClient.sql("""
                    UPDATE docks
                    SET bike_id = :bikeId, status = :status, updated_at = :updatedAt
                    WHERE dock_id = :dockId AND updated_at <= :updatedAt
                """)
                .param("bikeId", bikeId)
                .param("status", status)
                .param("updatedAt", java.sql.Timestamp.from(updatedAt))
                .param("dockId", dockId)
                .update();
    }
}