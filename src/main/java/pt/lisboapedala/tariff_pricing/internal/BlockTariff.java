package pt.lisboapedala.tariff_pricing.internal;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tarifa por blocos: os primeiros {@code includedMinutes} não são cobrados e depois cada bloco de
 * {@code blockMinutes} começado custa {@code pricePerBlock} (ADR-004 §5: blocos de 15 min).
 *
 * @param includedMinutes Minutos incluídos sem custo.
 * @param blockMinutes    Dimensão de cada bloco cobrado, em minutos.
 * @param pricePerBlock   Valor em euros de cada bloco começado.
 */
record BlockTariff(long includedMinutes, long blockMinutes, BigDecimal pricePerBlock) implements TariffRule {

    BlockTariff {
        Objects.requireNonNull(pricePerBlock, "pricePerBlock não pode ser nulo");
        if (includedMinutes < 0) {
            throw new IllegalArgumentException("includedMinutes não pode ser negativo");
        }
        if (blockMinutes <= 0) {
            throw new IllegalArgumentException("blockMinutes tem de ser positivo");
        }
        if (pricePerBlock.signum() < 0) {
            throw new IllegalArgumentException("pricePerBlock não pode ser negativo");
        }
    }

    @Override
    public BigDecimal price(long durationMinutes) {
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes não pode ser negativo");
        }
        long charged = Math.max(0, durationMinutes - includedMinutes);
        long blocks = Math.ceilDiv(charged, blockMinutes);
        return pricePerBlock.multiply(BigDecimal.valueOf(blocks));
    }
}
