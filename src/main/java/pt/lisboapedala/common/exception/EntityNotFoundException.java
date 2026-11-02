package pt.lisboapedala.common.exception;

/**
 * Lançada quando um recurso ou entidade solicitada não é encontrada no sistema.
 * Corresponde ao código de erro NOT_FOUND (HTTP 404).
 */
public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public EntityNotFoundException(String entityName, Object id) {
        super(ErrorCode.NOT_FOUND, String.format("%s com o identificador '%s' não foi encontrado(a).", entityName, id));
    }
}