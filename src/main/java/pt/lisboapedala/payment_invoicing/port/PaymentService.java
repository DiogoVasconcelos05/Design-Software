package pt.lisboapedala.payment_invoicing.port;

import java.util.List;
import pt.lisboapedala.common.valueobject.TripSummary;

/**
 * Porta de escrita publicada por payment_invoicing (ARCHITECTURE.md §7.3, endpoints 3 e 7, e §7.4.2).
 */
public interface PaymentService {

    /**
     * Cobra a viagem e emite a fatura. Idempotente por {@code summary.tripId()} (ADR-004 §4):
     * chamadas repetidas para a mesma viagem não criam novas cobranças nem faturas.
     *
     * <p>Devolver sem exceção significa que o pedido ficou registado; uma recusa do PSP ou uma falha da AT
     * ficam a cargo dos reenvios de payment_invoicing. Uma exceção significa que nada ficou registado.
     */
    void charge(TripSummary summary);

    /** Regista (ou substitui) o meio de pagamento do utilizador usado nas cobranças seguintes. */
    void registerPaymentMethod(String userId, PaymentMethodKind kind, String token);

    /** Faturas do utilizador, mais recente primeiro. */
    List<InvoiceView> invoicesOf(String userId, int limit, int offset);
}
