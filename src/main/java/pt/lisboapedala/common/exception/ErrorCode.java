package pt.lisboapedala.common.exception;

/**
 * Enumerado que padroniza os códigos de erro de negócio do sistema Lisboa Pedala.
 * Mapeado na resposta Problem Details (RFC 7807) da API.
 */
public enum ErrorCode {
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    PASS_INVALID,
    DOCK_UNAVAILABLE,
    PAYMENT_FAILED,
    TAX_SUBMISSION_FAILED,
    INTERNAL
}