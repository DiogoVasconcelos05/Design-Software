package pt.lisboapedala.payment_invoicing.internal;

/**
 * Fornecedor de pagamentos externo (MB WAY / cartão, docs/diagramas C4 nível 3).
 */
public interface PaymentGateway {

    /**
     * Cobra o pedido. Idempotente por {@link PaymentOrder#idempotencyKey()}.
     *
     * @return referência da transação no PSP
     * @throws RuntimeException se o PSP recusar ou não responder
     */
    String collect(PaymentOrder order);
}
