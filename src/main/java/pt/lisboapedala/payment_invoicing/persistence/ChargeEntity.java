package pt.lisboapedala.payment_invoicing.persistence;

import java.time.Instant;
import java.util.UUID;
import pt.lisboapedala.common.valueobject.PassType;

/**
 * Linha da tabela {@code payment_charges}. Privada a payment_invoicing (FF-04b: só usada por {@code internal}).
 */
public record ChargeEntity(
        UUID tripId,
        String userId,
        PassType passType,
        long durationMinutes,
        long amountCents,
        ChargeStatus status,
        int attempts,
        String pspReference,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
