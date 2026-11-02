package pt.lisboapedala.tariff_pricing.internal;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pt.lisboapedala.common.valueobject.PassType;

/**
 * Tarifário de um município: uma regra por tipo de passe. Imutável; um município vizinho é só outra instância
 * (ADR-003), sem tocar em payment_invoicing.
 *
 * @param name  Nome do tarifário (ex.: município).
 * @param rules Regra aplicada a cada tipo de passe; tem de cobrir todos os {@link PassType}.
 */
record TariffSchedule(String name, Map<PassType, TariffRule> rules) {

    /** Bloco de faturação da viagem (ADR-004 §5). */
    static final long BLOCK_MINUTES = 15;

    TariffSchedule {
        Objects.requireNonNull(name, "name não pode ser nulo");
        rules = Map.copyOf(Objects.requireNonNull(rules, "rules não pode ser nulo"));
        Set<PassType> missing = EnumSet.allOf(PassType.class);
        missing.removeAll(rules.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("tarifário " + name + " sem regra para " + missing);
        }
    }

    /** Tarifário de Lisboa (valores em IMPLEMENTATION_NOTES.md). */
    static TariffSchedule lisboa() {
        return new TariffSchedule("Lisboa", Map.of(
                PassType.ANNUAL, new BlockTariff(45, BLOCK_MINUTES, new BigDecimal("0.50")),
                PassType.OCCASIONAL, new BlockTariff(0, BLOCK_MINUTES, new BigDecimal("1.00"))));
    }

    BigDecimal price(PassType passType, long durationMinutes) {
        return rules.get(Objects.requireNonNull(passType, "passType não pode ser nulo")).price(durationMinutes);
    }
}
