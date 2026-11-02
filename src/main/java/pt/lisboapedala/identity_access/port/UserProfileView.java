package pt.lisboapedala.identity_access.port;

import pt.lisboapedala.common.valueobject.PassType;

import java.time.Instant;
import java.util.Objects;

/**
 * Visão DTO imutável do perfil de um utilizador registado.
 */
public record UserProfileView(
        String userId,
        String name,
        String email,
        String naveganteCardNo,
        PassType passType,
        boolean active,
        Instant createdAt
) {
    public UserProfileView {
        Objects.requireNonNull(userId, "userId não pode ser nulo");
        Objects.requireNonNull(name, "name não pode ser nulo");
        Objects.requireNonNull(email, "email não pode ser nulo");
        Objects.requireNonNull(passType, "passType não pode ser nulo");
        Objects.requireNonNull(createdAt, "createdAt não pode ser nulo");
    }
}