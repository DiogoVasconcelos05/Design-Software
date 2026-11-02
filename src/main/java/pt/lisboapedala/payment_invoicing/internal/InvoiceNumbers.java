package pt.lisboapedala.payment_invoicing.internal;

/**
 * Formato do número fiscal da fatura a partir do número sequencial da série.
 */
final class InvoiceNumbers {

    private static final String SERIES = "FT LP/";

    private InvoiceNumbers() {
    }

    static String format(long number) {
        return SERIES + number;
    }
}
