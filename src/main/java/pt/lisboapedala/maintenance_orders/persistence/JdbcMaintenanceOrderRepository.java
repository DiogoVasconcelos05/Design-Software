package pt.lisboapedala.maintenance_orders.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;

/**
 * Implementação de {@link MaintenanceOrderRepository} com {@link JdbcClient} e SQL à mão (ARCHITECTURE.md §7.1).
 */
@Repository
public class JdbcMaintenanceOrderRepository implements MaintenanceOrderRepository {

    private static final String COLUMNS = "order_id, bike_id, fault_type, origin, status, description, reported_by, "
            + "technician_id, notes, opened_at, started_at, completed_at";

    private final JdbcClient jdbc;

    public JdbcMaintenanceOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code INSERT OR IGNORE}: uma ordem ativa repetida não lança exceção nem aborta a transação, só não insere. */
    @Override
    public boolean insertIfNoActive(MaintenanceOrderEntity order) {
        return jdbc.sql("INSERT OR IGNORE INTO maintenance_orders (" + COLUMNS + ") VALUES (:orderId, :bikeId, "
                        + ":faultType, :origin, :status, :description, :reportedBy, :technicianId, :notes, "
                        + ":openedAt, :startedAt, :completedAt)")
                .param("orderId", order.orderId().toString())
                .param("bikeId", order.bikeId())
                .param("faultType", order.faultType().name())
                .param("origin", order.origin().name())
                .param("status", order.status().name())
                .param("description", order.description())
                .param("reportedBy", order.reportedBy())
                .param("technicianId", order.technicianId())
                .param("notes", order.notes())
                .param("openedAt", millis(order.openedAt()))
                .param("startedAt", millis(order.startedAt()))
                .param("completedAt", millis(order.completedAt()))
                .update() == 1;
    }

    @Override
    public boolean update(MaintenanceOrderEntity order, MaintenanceOrderStatus expectedStatus) {
        return jdbc.sql("UPDATE maintenance_orders SET status = :status, technician_id = :technicianId, "
                        + "notes = :notes, started_at = :startedAt, completed_at = :completedAt "
                        + "WHERE order_id = :orderId AND status = :expectedStatus")
                .param("status", order.status().name())
                .param("technicianId", order.technicianId())
                .param("notes", order.notes())
                .param("startedAt", millis(order.startedAt()))
                .param("completedAt", millis(order.completedAt()))
                .param("orderId", order.orderId().toString())
                .param("expectedStatus", expectedStatus.name())
                .update() == 1;
    }

    @Override
    public Optional<MaintenanceOrderEntity> findById(UUID orderId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM maintenance_orders WHERE order_id = :orderId")
                .param("orderId", orderId.toString())
                .query(JdbcMaintenanceOrderRepository::map)
                .optional();
    }

    @Override
    public Optional<MaintenanceOrderEntity> findActive(String bikeId, FaultType faultType) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM maintenance_orders "
                        + "WHERE bike_id = :bikeId AND fault_type = :faultType AND status <> :completed")
                .param("bikeId", bikeId)
                .param("faultType", faultType.name())
                .param("completed", MaintenanceOrderStatus.COMPLETED.name())
                .query(JdbcMaintenanceOrderRepository::map)
                .optional();
    }

    @Override
    public List<MaintenanceOrderEntity> findByStatus(List<MaintenanceOrderStatus> statuses) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM maintenance_orders WHERE status IN (:statuses) "
                        + "ORDER BY opened_at, order_id")
                .param("statuses", statuses.stream().map(Enum::name).toList())
                .query(JdbcMaintenanceOrderRepository::map)
                .list();
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static MaintenanceOrderEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new MaintenanceOrderEntity(
                UUID.fromString(rs.getString("order_id")),
                rs.getString("bike_id"),
                FaultType.valueOf(rs.getString("fault_type")),
                MaintenanceOrigin.valueOf(rs.getString("origin")),
                MaintenanceOrderStatus.valueOf(rs.getString("status")),
                rs.getString("description"),
                rs.getString("reported_by"),
                rs.getString("technician_id"),
                rs.getString("notes"),
                instant(rs, "opened_at"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"));
    }
}
