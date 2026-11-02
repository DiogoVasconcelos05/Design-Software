package pt.lisboapedala.tariff_pricing.port;

import java.math.BigDecimal;

/**
 * Porta publicada por tariff_pricing para o cálculo do valor de uma viagem (ARCHITECTURE.md §7.4.2, ADR-003).
 * Usada por payment_invoicing. Função pura: o mesmo pedido dá sempre o mesmo valor, sem estado nem efeitos
 * secundários (ARCHITECTURE.md §3).
 */
public interface TariffPricing {

    /** Valor da viagem em euros, nunca negativo. */
    BigDecimal price(PricingRequest request);
}
