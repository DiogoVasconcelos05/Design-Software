package pt.lisboapedala.payment_invoicing.persistence;

import java.time.Instant;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;

/**
 * Linha da tabela {@code payment_methods}. O {@code token} é a referência opaca do PSP, nunca o número do cartão.
 */
public record PaymentMethodEntity(
        String userId,
        PaymentMethodKind kind,
        String token,
        Instant registeredAt
) {
}
