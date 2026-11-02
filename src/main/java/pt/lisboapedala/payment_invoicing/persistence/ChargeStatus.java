package pt.lisboapedala.payment_invoicing.persistence;

/**
 * Estado de uma cobrança na tabela {@code payment_charges}.
 */
public enum ChargeStatus {
    /** Registada; ainda sem resposta do PSP. */
    PENDING,
    /** Liquidada (ou sem valor a cobrar). */
    PAID,
    /** A última tentativa falhou; o job volta a tentar até {@code lisboapedala.payment.max-attempts}. */
    FAILED
}
