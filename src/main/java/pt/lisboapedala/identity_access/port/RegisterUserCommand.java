package pt.lisboapedala.identity_access.port;

import pt.lisboapedala.common.valueobject.PassType;

import java.util.Objects;

/**
 * Comando de escrita imutável para registar um novo utilizador na plataforma.
 *
 * @param name            Nome completo do utilizador.
 * @param email           Endereço de e-mail único.
 * @param naveganteCardNo Número do cartão Navegante (opcional/pode ser nulo).
 * @param passType        Tipo de passe atribuído no registo (ANNUAL ou OCCASIONAL).
 */
public record RegisterUserCommand(
        String name,
        String email,
        String naveganteCardNo,
        PassType passType
) {
    public RegisterUserCommand {
        Objects.requireNonNull(name, "nome não pode ser nulo");
        Objects.requireNonNull(email, "email não pode ser nulo");
        Objects.requireNonNull(passType, "passType não pode ser nulo");

        if (name.isBlank()) {
            throw new IllegalArgumentException("nome não pode estar em branco");
        }
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("email inválido");
        }
        if (naveganteCardNo != null && naveganteCardNo.isBlank()) {
            throw new IllegalArgumentException("número do cartão Navegante não pode ser vazio se fornecido");
        }
    }
}