package pt.lisboapedala.trip_management.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.trip_management.port.TripStatus;

/**
 * Implementação de {@link TripRepository} com {@link JdbcClient} e SQL à mão (ARCHITECTURE.md §7.1).
 */
@Repository
public class JdbcTripRepository implements TripRepository {

    private static final String COLUMNS = "trip_id, user_id, pass_type, status, origin_dock_id, destination_dock_id, "
            + "bike_id, requested_at, started_at, ended_at, start_event_id, close_event_id, billing_dispatched";

    private final JdbcClient jdbc;

    public JdbcTripRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(TripEntity trip) {
        jdbc.sql("INSERT INTO trips (" + COLUMNS + ") VALUES (:tripId, :userId, :passType, :status, :originDockId, "
                        + ":destinationDockId, :bikeId, :requestedAt, :startedAt, :endedAt, :startEventId, "
                        + ":closeEventId, :billingDispatched)")
                .param("tripId", trip.tripId().toString())
                .param("userId", trip.userId())
                .param("passType", trip.passType().name())
                .param("status", trip.status().name())
                .param("originDockId", trip.originDockId())
                .param("destinationDockId", trip.destinationDockId())
                .param("bikeId", trip.bikeId())
                .param("requestedAt", millis(trip.requestedAt()))
                .param("startedAt", millis(trip.startedAt()))
                .param("endedAt", millis(trip.endedAt()))
                .param("startEventId", text(trip.startEventId()))
                .param("closeEventId", text(trip.closeEventId()))
                .param("billingDispatched", trip.billingDispatched() ? 1 : 0)
                .update();
    }

    @Override
    public boolean update(TripEntity trip, TripStatus expectedStatus) {
        return jdbc.sql("UPDATE trips SET status = :status, destination_dock_id = :destinationDockId, "
                        + "bike_id = :bikeId, started_at = :startedAt, ended_at = :endedAt, "
                        + "start_event_id = :startEventId, close_event_id = :closeEventId, "
                        + "billing_dispatched = :billingDispatched "
                        + "WHERE trip_id = :tripId AND status = :expectedStatus")
                .param("status", trip.status().name())
                .param("destinationDockId", trip.destinationDockId())
                .param("bikeId", trip.bikeId())
                .param("startedAt", millis(trip.startedAt()))
                .param("endedAt", millis(trip.endedAt()))
                .param("startEventId", text(trip.startEventId()))
                .param("closeEventId", text(trip.closeEventId()))
                .param("billingDispatched", trip.billingDispatched() ? 1 : 0)
                .param("tripId", trip.tripId().toString())
                .param("expectedStatus", expectedStatus.name())
                .update() == 1;
    }

    @Override
    public Optional<TripEntity> findById(UUID tripId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM trips WHERE trip_id = :id")
                .param("id", tripId.toString())
                .query(JdbcTripRepository::map)
                .optional();
    }

    @Override
    public Optional<TripEntity> findByStartEventId(UUID eventId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM trips WHERE start_event_id = :id")
                .param("id", eventId.toString())
                .query(JdbcTripRepository::map)
                .optional();
    }

    @Override
    public Optional<TripEntity> findByCloseEventId(UUID eventId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM trips WHERE close_event_id = :id")
                .param("id", eventId.toString())
                .query(JdbcTripRepository::map)
                .optional();
    }

    @Override
    public Optional<TripEntity> findInProgressByBike(String bikeId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM trips WHERE bike_id = :bikeId AND status = :status "
                        + "ORDER BY started_at DESC LIMIT 1")
                .param("bikeId", bikeId)
                .param("status", TripStatus.IN_PROGRESS.name())
                .query(JdbcTripRepository::map)
                .optional();
    }

    @Override
    public boolean hasActiveTrip(String userId) {
        return jdbc.sql("SELECT COUNT(*) FROM trips WHERE user_id = :userId AND status IN (:requested, :inProgress)")
                .param("userId", userId)
                .param("requested", TripStatus.REQUESTED.name())
                .param("inProgress", TripStatus.IN_PROGRESS.name())
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public List<TripEntity> findByUser(String userId, int limit, int offset) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM trips WHERE user_id = :userId "
                        + "ORDER BY requested_at DESC, trip_id LIMIT :limit OFFSET :offset")
                .param("userId", userId)
                .param("limit", limit)
                .param("offset", offset)
                .query(JdbcTripRepository::map)
                .list();
    }

    @Override
    public int expireRequestedBefore(Instant cutoff) {
        return jdbc.sql("UPDATE trips SET status = :expired WHERE status = :requested AND requested_at < :cutoff")
                .param("expired", TripStatus.EXPIRED.name())
                .param("requested", TripStatus.REQUESTED.name())
                .param("cutoff", cutoff.toEpochMilli())
                .update();
    }

    @Override
    public List<TripEntity> findPendingBilling(int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM trips WHERE status = :closed AND billing_dispatched = 0 "
                        + "ORDER BY ended_at LIMIT :limit")
                .param("closed", TripStatus.CLOSED.name())
                .param("limit", limit)
                .query(JdbcTripRepository::map)
                .list();
    }

    /**
     * Corre numa transação própria: é chamado depois do commit do fecho, quando a transação original
     * já terminou e uma escrita nela não seria persistida.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markBillingDispatched(UUID tripId) {
        jdbc.sql("UPDATE trips SET billing_dispatched = 1 WHERE trip_id = :id")
                .param("id", tripId.toString())
                .update();
    }

    @Override
    public List<TripEntity> findClosedBetween(Instant from, Instant to, int limit, int offset) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM trips WHERE status = :closed AND ended_at >= :from "
                        + "AND ended_at < :to ORDER BY ended_at, trip_id LIMIT :limit OFFSET :offset")
                .param("closed", TripStatus.CLOSED.name())
                .param("from", from.toEpochMilli())
                .param("to", to.toEpochMilli())
                .param("limit", limit)
                .param("offset", offset)
                .query(JdbcTripRepository::map)
                .list();
    }

    private static TripEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new TripEntity(
                UUID.fromString(rs.getString("trip_id")),
                rs.getString("user_id"),
                PassType.valueOf(rs.getString("pass_type")),
                TripStatus.valueOf(rs.getString("status")),
                rs.getString("origin_dock_id"),
                rs.getString("destination_dock_id"),
                rs.getString("bike_id"),
                instant(rs, "requested_at"),
                instant(rs, "started_at"),
                instant(rs, "ended_at"),
                uuid(rs.getString("start_event_id")),
                uuid(rs.getString("close_event_id")),
                rs.getInt("billing_dispatched") == 1);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static String text(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
