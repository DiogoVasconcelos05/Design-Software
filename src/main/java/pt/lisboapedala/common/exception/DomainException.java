package pt.lisboapedala.common.exception;

import java.util.Objects;

/**
 * Exceção base de domínio para o sistema Lisboa Pedala.
 * Todas as exceções genéricas ou específicas de negócio derivam desta classe.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode, String message) {
        super(Objects.requireNonNull(message, "A mensagem de erro não pode ser nula"));
        this.errorCode = Objects.requireNonNull(errorCode, "O ErrorCode não pode ser nulo");
    }

    public DomainException(ErrorCode errorCode, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "A mensagem de erro não pode ser nula"), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "O ErrorCode não pode ser nulo");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}