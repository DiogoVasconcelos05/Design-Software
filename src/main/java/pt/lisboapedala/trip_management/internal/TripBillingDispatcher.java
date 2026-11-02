package pt.lisboapedala.trip_management.internal;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pt.lisboapedala.common.valueobject.TripSummary;
import pt.lisboapedala.payment_invoicing.port.PaymentService;
import pt.lisboapedala.trip_management.persistence.TripEntity;
import pt.lisboapedala.trip_management.persistence.TripRepository;

/**
 * Entrega a cobrança de viagens fechadas a payment_invoicing, sempre fora da transação de fecho
 * (ARCHITECTURE.md §4, ADR-004 §4). Funciona como outbox: {@code billing_dispatched} só passa a 1 depois
 * de {@link PaymentService#charge} aceitar o pedido, e o job reenvia as que ficaram por entregar.
 * Reenviar é seguro porque a cobrança é idempotente por tripId.
 */
@Component
public class TripBillingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TripBillingDispatcher.class);
    private static final int RETRY_BATCH_SIZE = 100;

    private final PaymentService payments;
    private final TripRepository trips;

    public TripBillingDispatcher(PaymentService payments, TripRepository trips) {
        this.payments = payments;
        this.trips = trips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripClosed(TripClosedEvent event) {
        dispatch(event.summary());
    }

    @Scheduled(fixedDelayString = "${lisboapedala.trip.billing-retry-interval-seconds}",
            initialDelayString = "${lisboapedala.trip.billing-retry-interval-seconds}",
            timeUnit = TimeUnit.SECONDS)
    public void retryPendingBilling() {
        for (TripEntity pending : trips.findPendingBilling(RETRY_BATCH_SIZE)) {
            dispatch(Trip.fromEntity(pending).toSummary());
        }
    }

    boolean dispatch(TripSummary summary) {
        try {
            payments.charge(summary);
        } catch (RuntimeException e) {
            log.warn("Cobrança da viagem {} por entregar; será reenviada", summary.tripId(), e);
            return false;
        }
        trips.markBillingDispatched(summary.tripId());
        return true;
    }
}
