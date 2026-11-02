package pt.lisboapedala.payment_invoicing.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;

/**
 * Implementação de {@link PaymentMethodRepository} com {@link JdbcClient} e SQL à mão (ARCHITECTURE.md §7.1).
 */
@Repository
public class JdbcPaymentMethodRepository implements PaymentMethodRepository {

    private final JdbcClient jdbc;

    public JdbcPaymentMethodRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PaymentMethodEntity method) {
        jdbc.sql("INSERT INTO payment_methods (user_id, kind, token, registered_at) "
                        + "VALUES (:userId, :kind, :token, :registeredAt) "
                        + "ON CONFLICT (user_id) DO UPDATE SET kind = excluded.kind, token = excluded.token, "
                        + "registered_at = excluded.registered_at")
                .param("userId", method.userId())
                .param("kind", method.kind().name())
                .param("token", method.token())
                .param("registeredAt", method.registeredAt().toEpochMilli())
                .update();
    }

    @Override
    public Optional<PaymentMethodEntity> findByUser(String userId) {
        return jdbc.sql("SELECT user_id, kind, token, registered_at FROM payment_methods WHERE user_id = :userId")
                .param("userId", userId)
                .query(JdbcPaymentMethodRepository::map)
                .optional();
    }

    private static PaymentMethodEntity map(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentMethodEntity(
                rs.getString("user_id"),
                PaymentMethodKind.valueOf(rs.getString("kind")),
                rs.getString("token"),
                Instant.ofEpochMilli(rs.getLong("registered_at")));
    }
}
