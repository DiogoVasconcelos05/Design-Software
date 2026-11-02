package pt.lisboapedala.payment_invoicing.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Dados da fatura comunicados à Autoridade Tributária.
 *
 * @param invoiceNumber Número fiscal da fatura; identifica a comunicação (repeti-la é seguro).
 * @param invoiceId     Identificador interno da fatura.
 * @param amountCents   Valor total em cêntimos de euro.
 * @param issuedAt      Instante de emissão.
 */
public record TaxDocument(String invoiceNumber, UUID invoiceId, long amountCents, Instant issuedAt) {
}
