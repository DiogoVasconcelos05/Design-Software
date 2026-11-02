package pt.lisboapedala.payment_invoicing.port;

/**
 * Estado da comunicação de uma fatura à Autoridade Tributária ({@code at_status}, ARCHITECTURE.md §7.4.2).
 */
public enum TaxSubmissionStatus {
    /** Emitida, ainda por comunicar à AT; o job de reenvio volta a tentar. */
    PENDING,
    /** Aceite pela AT. */
    SUBMITTED
}
