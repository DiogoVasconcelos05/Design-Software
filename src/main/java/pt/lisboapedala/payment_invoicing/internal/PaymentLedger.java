package pt.lisboapedala.payment_invoicing.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.payment_invoicing.persistence.ChargeEntity;
import pt.lisboapedala.payment_invoicing.persistence.ChargeRepository;
import pt.lisboapedala.payment_invoicing.persistence.InvoiceEntity;
import pt.lisboapedala.payment_invoicing.persistence.InvoiceRepository;
import pt.lisboapedala.payment_invoicing.persistence.PaymentMethodEntity;
import pt.lisboapedala.payment_invoicing.persistence.PaymentMethodRepository;
import pt.lisboapedala.payment_invoicing.port.TaxSubmissionStatus;

/**
 * Todas as escritas de payment_invoicing, cada uma numa transação curta e própria. Nunca há chamadas ao PSP
 * ou à AT dentro destas transações (FF-14b: o SQLite tem um único escritor).
 *
 * <p>{@code REQUIRES_NEW} porque a cobrança é pedida por trip_management depois do commit do fecho
 * (ADR-004 §4), quando a transação original ainda está associada à thread e uma escrita nela não seria
 * persistida.
 */
@Component
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentLedger {

    private final ChargeRepository charges;
    private final InvoiceRepository invoices;
    private final PaymentMethodRepository methods;
    private final Clock clock;

    public PaymentLedger(ChargeRepository charges, InvoiceRepository invoices, PaymentMethodRepository methods,
                         Clock clock) {
        this.charges = charges;
        this.invoices = invoices;
        this.methods = methods;
        this.clock = clock;
    }

    /** Regista a cobrança PENDING; false se a viagem já tinha cobrança (ADR-004 §4). */
    public boolean open(ChargeEntity charge) {
        return charges.insertIfAbsent(charge);
    }

    public void recordChargeFailure(UUID tripId, String error) {
        charges.markFailed(tripId, error, Instant.now(clock));
    }

    /**
     * Marca a cobrança PAID e emite a fatura na mesma transação, para não haver cobrança paga sem fatura.
     * Uma viagem sem valor a cobrar não tem fatura.
     *
     * @return a fatura da viagem (nova ou já existente), por comunicar à AT se ainda estiver PENDING
     */
    public Optional<InvoiceEntity> settle(ChargeEntity charge, String pspReference) {
        Instant now = Instant.now(clock);
        charges.markPaid(charge.tripId(), pspReference, now);
        if (charge.amountCents() == 0) {
            return Optional.empty();
        }
        invoices.insertIfAbsent(new InvoiceEntity(null, UUID.randomUUID(), charge.tripId(), charge.userId(),
                charge.amountCents(), now, TaxSubmissionStatus.PENDING, null, 0));
        return invoices.findByTripId(charge.tripId());
    }

    public void recordTaxSubmission(UUID invoiceId, String atReference) {
        invoices.markSubmitted(invoiceId, atReference, Instant.now(clock));
    }

    public void recordTaxFailure(UUID invoiceId, String error) {
        invoices.markSubmissionFailed(invoiceId, error, Instant.now(clock));
    }

    public void savePaymentMethod(PaymentMethodEntity method) {
        methods.save(method);
    }
}
