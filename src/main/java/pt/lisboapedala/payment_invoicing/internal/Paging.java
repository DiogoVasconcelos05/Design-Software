package pt.lisboapedala.payment_invoicing.internal;

import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;

/**
 * Limites de paginação das consultas de payment_invoicing.
 */
final class Paging {

    static final int MAX_LIMIT = 100;

    private Paging() {
    }

    static void validate(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "limit tem de estar entre 1 e " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "offset não pode ser negativo");
        }
    }
}
