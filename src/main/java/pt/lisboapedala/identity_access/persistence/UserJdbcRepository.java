package pt.lisboapedala.identity_access.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Repositório baseado em Spring JdbcClient sem JPA (FF-12).
 * PRIVADO AO MÓDULO.
 */
@Repository
public class UserJdbcRepository {

    private final JdbcClient jdbcClient;

    public UserJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(UserEntity user) {
        String sql = """
            INSERT INTO ia_users (id, name, email, navegante_card_no, pass_type, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        this.jdbcClient.sql(sql)
                .param(user.id())
                .param(user.name())
                .param(user.email())
                .param(user.naveganteCardNo())
                .param(user.passType().name())
                .param(user.active() ? 1 : 0)
                .param(user.createdAt().toString())
                .update();
    }

    public Optional<UserEntity> findById(String id) {
        String sql = "SELECT id, name, email, navegante_card_no, pass_type, active, created_at FROM ia_users WHERE id = ?";
        return this.jdbcClient.sql(sql)
                .param(id)
                .query(this::mapRow)
                .optional();
    }

    public Optional<UserEntity> findByEmail(String email) {
        String sql = "SELECT id, name, email, navegante_card_no, pass_type, active, created_at FROM ia_users WHERE email = ?";
        return this.jdbcClient.sql(sql)
                .param(email)
                .query(this::mapRow)
                .optional();
    }

    public Optional<UserEntity> findByNaveganteCardNo(String naveganteCardNo) {
        String sql = "SELECT id, name, email, navegante_card_no, pass_type, active, created_at FROM ia_users WHERE navegante_card_no = ?";
        return this.jdbcClient.sql(sql)
                .param(naveganteCardNo)
                .query(this::mapRow)
                .optional();
    }

    private UserEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UserEntity(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("navegante_card_no"),
                pt.lisboapedala.common.valueobject.PassType.valueOf(rs.getString("pass_type")),
                rs.getInt("active") == 1,
                Instant.parse(rs.getString("created_at"))
        );
    }
}