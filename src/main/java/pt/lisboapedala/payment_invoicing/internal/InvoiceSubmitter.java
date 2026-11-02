package pt.lisboapedala.payment_invoicing.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.lisboapedala.payment_invoicing.persistence.InvoiceEntity;
import pt.lisboapedala.payment_invoicing.port.TaxSubmissionStatus;

/**
 * Comunica faturas emitidas à AT. Uma falha deixa a fatura PENDING para o job de reenvio
 * (ARCHITECTURE.md §7.4.2); a AT é idempotente pelo número da fatura.
 */
@Component
public class InvoiceSubmitter {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSubmitter.class);

    private final TaxAuthorityGateway taxAuthority;
    private final PaymentLedger ledger;

    public InvoiceSubmitter(TaxAuthorityGateway taxAuthority, PaymentLedger ledger) {
        this.taxAuthority = taxAuthority;
        this.ledger = ledger;
    }

    void submit(InvoiceEntity invoice) {
        if (invoice.atStatus() == TaxSubmissionStatus.SUBMITTED) {
            return;
        }
        String number = InvoiceNumbers.format(invoice.number());
        String atReference;
        try {
            atReference = taxAuthority.submit(
                    new TaxDocument(number, invoice.invoiceId(), invoice.amountCents(), invoice.issuedAt()));
        } catch (RuntimeException e) {
            ledger.recordTaxFailure(invoice.invoiceId(), ChargeSettlement.reasonOf(e));
            log.warn("Comunicação da fatura {} à AT falhou (tentativa {}); será reenviada",
                    number, invoice.atAttempts() + 1, e);
            return;
        }
        ledger.recordTaxSubmission(invoice.invoiceId(), atReference);
        log.info("Fatura {} comunicada à AT ({})", number, atReference);
    }
}
