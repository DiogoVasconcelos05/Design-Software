package pt.lisboapedala.payment_invoicing.persistence;

import java.time.Instant;
import java.util.UUID;
import pt.lisboapedala.payment_invoicing.port.TaxSubmissionStatus;

/**
 * Linha da tabela {@code invoices}. Privada a payment_invoicing (FF-04b: só usada por {@code internal}).
 *
 * @param number Número sequencial atribuído pela BD; null antes de a fatura ser gravada.
 */
public record InvoiceEntity(
        Long number,
        UUID invoiceId,
        UUID tripId,
        String userId,
        long amountCents,
        Instant issuedAt,
        TaxSubmissionStatus atStatus,
        String atReference,
        int atAttempts
) {
}
