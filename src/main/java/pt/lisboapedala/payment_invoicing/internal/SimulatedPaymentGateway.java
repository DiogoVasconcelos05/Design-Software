package pt.lisboapedala.payment_invoicing.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;

/**
 * PSP simulado (ARCHITECTURE.md §0.3 e §7.4.2): aprova todas as cobranças válidas, sem cliente HTTP.
 * A referência deriva da chave de idempotência, por isso repetir o pedido devolve a mesma transação.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    @Override
    public String collect(PaymentOrder order) {
        if (order.amountCents() <= 0 || order.token() == null || order.token().isBlank()) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_FAILED, "Pedido de cobrança inválido para o PSP");
        }
        String reference = "PSP-" + order.idempotencyKey();
        log.info("PSP simulado: {} cêntimos por {} aprovados ({})", order.amountCents(), order.kind(), reference);
        return reference;
    }
}
