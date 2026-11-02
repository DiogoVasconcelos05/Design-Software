package pt.lisboapedala.common.valueobject;

/**
 * Estados possíveis do resultado da validação de um passe/cartão Navegante.
 */
public enum PassValidationStatus {
    VALID,
    EXPIRED,
    INSUFFICIENT_BALANCE,
    SUSPENDED,
    NOT_FOUND
}