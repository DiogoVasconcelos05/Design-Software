package pt.lisboapedala.payment_invoicing.internal;

import java.util.UUID;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;

/**
 * Pedido de cobrança enviado ao PSP.
 *
 * @param idempotencyKey Chave de idempotência do PSP: o tripId (ADR-004 §4), para que tentativas repetidas ou
 *                       concorrentes da mesma viagem nunca cobrem duas vezes.
 * @param kind           Meio de pagamento.
 * @param token          Referência opaca do meio de pagamento no PSP.
 * @param amountCents    Valor em cêntimos de euro, positivo.
 */
public record PaymentOrder(UUID idempotencyKey, PaymentMethodKind kind, String token, long amountCents) {
}
