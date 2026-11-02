package pt.lisboapedala.common.valueobject;

import java.util.Objects;

/**
 * Credencial apresentada pelo utilizador na doca (código QR ou cartão Navegante).
 * Utilizada na comunicação entre api, trip_management e identity_access.
 *
 * @param kind  Tipo de credencial.
 * @param value Valor lido da credencial (conteúdo do QR ou número do cartão).
 */
public record Credential(
        CredentialKind kind,
        String value
) {
    public Credential {
        Objects.requireNonNull(kind, "kind não pode ser nulo");
        Objects.requireNonNull(value, "value não pode ser nulo");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value não pode estar em branco");
        }
    }
}
