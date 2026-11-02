package pt.lisboapedala.payment_invoicing.port;

import pt.lisboapedala.common.valueobject.TripSummary;

/**
 * Porta de escrita publicada por payment_invoicing para cobrar uma viagem terminada (ARCHITECTURE.md §7.4.2).
 */
public interface PaymentService {

    /**
     * Cobra a viagem e emite a fatura. Idempotente por {@code summary.tripId()} (ADR-004 §4):
     * chamadas repetidas para a mesma viagem não criam novas cobranças nem faturas.
     */
    void charge(TripSummary summary);
}
