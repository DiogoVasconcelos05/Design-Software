package pt.lisboapedala.payment_invoicing.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Vista imutável de uma fatura devolvida à api (endpoint 7 de ARCHITECTURE.md §7.3).
 *
 * @param invoiceId     Identificador da fatura.
 * @param invoiceNumber Número fiscal sequencial (ex.: {@code FT LP/42}).
 * @param tripId        Viagem faturada (no máximo uma fatura por viagem, ADR-004 §4).
 * @param userId        Utilizador faturado.
 * @param amount        Valor em euros, com IVA incluído.
 * @param issuedAt      Instante de emissão.
 * @param atStatus      Estado da comunicação à AT.
 * @param atReference   Referência devolvida pela AT; null enquanto {@code PENDING}.
 */
public record InvoiceView(
        UUID invoiceId,
        String invoiceNumber,
        UUID tripId,
        String userId,
        BigDecimal amount,
        Instant issuedAt,
        TaxSubmissionStatus atStatus,
        String atReference
) {
}
