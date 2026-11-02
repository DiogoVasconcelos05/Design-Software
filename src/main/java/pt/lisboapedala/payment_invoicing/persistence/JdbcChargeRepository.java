package pt.lisboapedala.payment_invoicing.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.lisboapedala.common.valueobject.PassType;

/**
 * Implementação de {@link ChargeRepository} com {@link JdbcClient} e SQL à mão (ARCHITECTURE.md §7.1).
 */
@Repository
public class JdbcChargeRepository implements ChargeRepository {

    private static final String COLUMNS = "trip_id, user_id, pass_type, duration_minutes, amount_cents, status, "
            + "attempts, psp_reference, last_error, created_at, updated_at";

    private final JdbcClient jdbc;

    public JdbcChargeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(ChargeEntity charge) {
        return jdbc.sql("INSERT OR IGNORE INTO payment_charges (" + COLUMNS + ") VALUES (:tripId, :userId, "
                        + ":passType, :durationMinutes, :amountCents, :status, :attempts, :pspReference, "
                        + ":lastError, :createdAt, :updatedAt)")
                .param("tripId", charge.tripId().toString())
                .param("userId", charge.userId())
                .param("passType", charge.passType().name())
                .param("durationMinutes", charge.durationMinutes())
                .param("amountCents", charge.amountCents())
                .param("status", charge.status().name())
                .param("attempts", charge.attempts())
                .param("pspReference", charge.pspReference())
                .param("lastError", charge.lastError())
                .param("createdAt", charge.createdAt().toEpochMilli())
                .param("updatedAt", charge.updatedAt().toEpochMilli())
                .update() == 1;
    }

    @Override
    public Optional<ChargeEntity> findByTripId(UUID tripId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payment_charges WHERE trip_id = :id")
                .param("id", tripId.toString())
                .query(JdbcChargeRepository::map)
                .optional();
    }

    @Override
    public boolean markPaid(UUID tripId, String pspReference, Instant at) {
        return jdbc.sql("UPDATE payment_charges SET status = :paid, psp_reference = :pspReference, "
                        + "last_error = NULL, attempts = attempts + 1, updated_at = :at "
                        + "WHERE trip_id = :id AND status <> :paid")
                .param("paid", ChargeStatus.PAID.name())
                .param("pspReference", pspReference)
                .param("at", at.toEpochMilli())
                .param("id", tripId.toString())
                .update() == 1;
    }

    @Override
    public void markFailed(UUID tripId, String error, Instant at) {
        jdbc.sql("UPDATE payment_charges SET status = :failed, last_error = :error, attempts = attempts + 1, "
                        + "updated_at = :at WHERE trip_id = :id AND status <> :paid")
                .param("failed", ChargeStatus.FAILED.name())
                .param("error", error)
                .param("at", at.toEpochMilli())
                .param("id", tripId.toString())
                .param("paid", ChargeStatus.PAID.name())
                .update();
    }

    @Override
    public List<ChargeEntity> findRetryable(int maxAttempts, Instant cutoff, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payment_charges WHERE status IN (:pending, :failed) "
                        + "AND attempts < :maxAttempts AND updated_at < :cutoff ORDER BY updated_at LIMIT :limit")
                .param("pending", ChargeStatus.PENDING.name())
                .param("failed", ChargeStatus.FAILED.name())
                .param("maxAttempts", maxAttempts)
                .param("cutoff", cutoff.toEpochMilli())
                .param("limit", limit)
                .query(JdbcChargeRepository::map)
                .list();
    }

    private static ChargeEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new ChargeEntity(
                UUID.fromString(rs.getString("trip_id")),
                rs.getString("user_id"),
                PassType.valueOf(rs.getString("pass_type")),
                rs.getLong("duration_minutes"),
                rs.getLong("amount_cents"),
                ChargeStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getString("psp_reference"),
                rs.getString("last_error"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("updated_at")));
    }
}
