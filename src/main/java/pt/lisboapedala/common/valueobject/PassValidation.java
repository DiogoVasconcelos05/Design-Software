package pt.lisboapedala.common.valueobject;

import java.util.Objects;

/**
 * Objeto de valor imutável que representa o resultado da validação do passe do utilizador.
 * Utilizado na comunicação entre identity_access e trip_management.
 *
 * @param userId   Identificador do utilizador titular do passe.
 * @param status   Estado da validação do passe.
 * @param details  Informação detalhada suplementar sobre o resultado da validação.
 */
public record PassValidation(
        String userId,
        PassValidationStatus status,
        String details
) {
    public PassValidation {
        Objects.requireNonNull(userId, "userId não pode ser nulo");
        Objects.requireNonNull(status, "status não pode ser nulo");
        
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId não pode estar em branco");
        }
    }

    public boolean isValid() {
        return status == PassValidationStatus.VALID;
    }
}