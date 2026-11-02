package pt.lisboapedala.tariff_pricing.internal;

import java.math.BigDecimal;

/**
 * Regra de preço de um tarifário para um tipo de passe (ADR-003). Implementações têm de ser puras e imutáveis
 * (ARCHITECTURE.md §3), para que novas regras de municípios vizinhos se acrescentem sem efeitos secundários.
 */
interface TariffRule {

    /** Valor em euros de uma viagem com a duração dada (minutos, já arredondados para cima), nunca negativo. */
    BigDecimal price(long durationMinutes);
}
