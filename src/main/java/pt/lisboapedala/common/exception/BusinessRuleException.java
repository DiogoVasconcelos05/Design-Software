package pt.lisboapedala.common.exception;

/**
 * Lançada quando uma regra ou invariante de negócio é violada.
 * Corresponde por omissão ao código CONFLICT (HTTP 409), podendo especificar outro ErrorCode.
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}