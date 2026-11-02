package pt.lisboapedala.payment_invoicing.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.lisboapedala.payment_invoicing.persistence.ChargeEntity;
import pt.lisboapedala.payment_invoicing.persistence.ChargeRepository;
import pt.lisboapedala.payment_invoicing.persistence.InvoiceEntity;
import pt.lisboapedala.payment_invoicing.persistence.InvoiceRepository;

/**
 * Outbox de payment_invoicing (ARCHITECTURE.md §7.4.2): volta a tentar as cobranças PENDING/FAILED até
 * {@code lisboapedala.payment.max-attempts} e reenvia à AT as faturas com {@code at_status = PENDING}.
 * Seguro com várias réplicas: o PSP é idempotente por tripId e a AT pelo número da fatura.
 */
@Component
public class PaymentRetryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentRetryJob.class);
    private static final int RETRY_BATCH_SIZE = 100;

    private final ChargeRepository charges;
    private final InvoiceRepository invoices;
    private final ChargeSettlement settlement;
    private final InvoiceSubmitter submitter;
    private final Clock clock;
    private final Duration retryInterval;
    private final int maxAttempts;

    public PaymentRetryJob(ChargeRepository charges, InvoiceRepository invoices, ChargeSettlement settlement,
                           InvoiceSubmitter submitter, Clock clock,
                           @Value("${lisboapedala.payment.retry-interval-seconds}") long retryIntervalSeconds,
                           @Value("${lisboapedala.payment.max-attempts}") int maxAttempts) {
        this.charges = charges;
        this.invoices = invoices;
        this.settlement = settlement;
        this.submitter = submitter;
        this.clock = clock;
        this.retryInterval = Duration.ofSeconds(retryIntervalSeconds);
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${lisboapedala.payment.retry-interval-seconds}",
            initialDelayString = "${lisboapedala.payment.retry-interval-seconds}",
            timeUnit = TimeUnit.SECONDS)
    public void retryPending() {
        Instant cutoff = Instant.now(clock).minus(retryInterval);
        int retried = retryCharges(cutoff);
        int resubmitted = resubmitInvoices(cutoff);
        if (retried + resubmitted > 0) {
            log.info("Reenvios: {} cobranças e {} faturas à AT", retried, resubmitted);
        }
    }

    private int retryCharges(Instant cutoff) {
        List<ChargeEntity> pending = charges.findRetryable(maxAttempts, cutoff, RETRY_BATCH_SIZE);
        pending.forEach(settlement::attempt);
        return pending.size();
    }

    private int resubmitInvoices(Instant cutoff) {
        List<InvoiceEntity> pending = invoices.findPendingSubmission(cutoff, RETRY_BATCH_SIZE);
        pending.forEach(submitter::submit);
        return pending.size();
    }
}
