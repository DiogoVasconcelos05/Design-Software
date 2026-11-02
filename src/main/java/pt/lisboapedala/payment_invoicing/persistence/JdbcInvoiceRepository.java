package pt.lisboapedala.payment_invoicing.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.lisboapedala.payment_invoicing.port.TaxSubmissionStatus;

/**
 * Implementação de {@link InvoiceRepository} com {@link JdbcClient} e SQL à mão (ARCHITECTURE.md §7.1).
 */
@Repository
public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String COLUMNS = "invoice_number, invoice_id, trip_id, user_id, amount_cents, issued_at, "
            + "at_status, at_reference, at_attempts";

    private final JdbcClient jdbc;

    public JdbcInvoiceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(InvoiceEntity invoice) {
        return jdbc.sql("INSERT OR IGNORE INTO invoices (invoice_id, trip_id, user_id, amount_cents, issued_at, "
                        + "at_status, at_reference, at_attempts) VALUES (:invoiceId, :tripId, :userId, :amountCents, "
                        + ":issuedAt, :atStatus, :atReference, :atAttempts)")
                .param("invoiceId", invoice.invoiceId().toString())
                .param("tripId", invoice.tripId().toString())
                .param("userId", invoice.userId())
                .param("amountCents", invoice.amountCents())
                .param("issuedAt", invoice.issuedAt().toEpochMilli())
                .param("atStatus", invoice.atStatus().name())
                .param("atReference", invoice.atReference())
                .param("atAttempts", invoice.atAttempts())
                .update() == 1;
    }

    @Override
    public Optional<InvoiceEntity> findByTripId(UUID tripId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invoices WHERE trip_id = :id")
                .param("id", tripId.toString())
                .query(JdbcInvoiceRepository::map)
                .optional();
    }

    @Override
    public List<InvoiceEntity> findByUser(String userId, int limit, int offset) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invoices WHERE user_id = :userId "
                        + "ORDER BY issued_at DESC, invoice_number DESC LIMIT :limit OFFSET :offset")
                .param("userId", userId)
                .param("limit", limit)
                .param("offset", offset)
                .query(JdbcInvoiceRepository::map)
                .list();
    }

    @Override
    public void markSubmitted(UUID invoiceId, String atReference, Instant at) {
        jdbc.sql("UPDATE invoices SET at_status = :submitted, at_reference = :atReference, "
                        + "at_attempts = at_attempts + 1, at_last_attempt_at = :at, at_last_error = NULL "
                        + "WHERE invoice_id = :id AND at_status = :pending")
                .param("submitted", TaxSubmissionStatus.SUBMITTED.name())
                .param("atReference", atReference)
                .param("at", at.toEpochMilli())
                .param("id", invoiceId.toString())
                .param("pending", TaxSubmissionStatus.PENDING.name())
                .update();
    }

    @Override
    public void markSubmissionFailed(UUID invoiceId, String error, Instant at) {
        jdbc.sql("UPDATE invoices SET at_attempts = at_attempts + 1, at_last_attempt_at = :at, "
                        + "at_last_error = :error WHERE invoice_id = :id AND at_status = :pending")
                .param("at", at.toEpochMilli())
                .param("error", error)
                .param("id", invoiceId.toString())
                .param("pending", TaxSubmissionStatus.PENDING.name())
                .update();
    }

    @Override
    public List<InvoiceEntity> findPendingSubmission(Instant cutoff, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invoices WHERE at_status = :pending "
                        + "AND COALESCE(at_last_attempt_at, issued_at) < :cutoff ORDER BY invoice_number LIMIT :limit")
                .param("pending", TaxSubmissionStatus.PENDING.name())
                .param("cutoff", cutoff.toEpochMilli())
                .param("limit", limit)
                .query(JdbcInvoiceRepository::map)
                .list();
    }

    private static InvoiceEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new InvoiceEntity(
                rs.getLong("invoice_number"),
                UUID.fromString(rs.getString("invoice_id")),
                UUID.fromString(rs.getString("trip_id")),
                rs.getString("user_id"),
                rs.getLong("amount_cents"),
                Instant.ofEpochMilli(rs.getLong("issued_at")),
                TaxSubmissionStatus.valueOf(rs.getString("at_status")),
                rs.getString("at_reference"),
                rs.getInt("at_attempts"));
    }
}
