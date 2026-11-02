package pt.lisboapedala.identity_access.persistence;

import pt.lisboapedala.common.valueobject.PassType;

import java.time.Instant;

/**
 * Representação da linha da tabela 'ia_users' (PRIVADO AO MÓDULO).
 */
public record UserEntity(
        String id,
        String name,
        String email,
        String naveganteCardNo,
        PassType passType,
        boolean active,
        Instant createdAt
) {
}