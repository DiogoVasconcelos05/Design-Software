package pt.lisboapedala.payment_invoicing.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversão entre euros (contrato de tariff_pricing e da api) e cêntimos (persistência).
 */
final class Money {

    private Money() {
    }

    static long toCents(BigDecimal euros) {
        if (euros == null || euros.signum() < 0) {
            throw new IllegalStateException("tariff_pricing devolveu um valor inválido: " + euros);
        }
        return euros.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    static BigDecimal toEuros(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
