package pt.lisboapedala.tariff_pricing.internal;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;
import pt.lisboapedala.tariff_pricing.port.PricingRequest;
import pt.lisboapedala.tariff_pricing.port.TariffPricing;

/**
 * Implementação de {@link TariffPricing} (ADR-003): função pura sobre um {@link TariffSchedule} imutável,
 * sem relógio, I/O nem estado (FF-10).
 */
@Component
public class DefaultTariffPricing implements TariffPricing {

    private final TariffSchedule schedule;

    public DefaultTariffPricing() {
        this(TariffSchedule.lisboa());
    }

    DefaultTariffPricing(TariffSchedule schedule) {
        this.schedule = Objects.requireNonNull(schedule, "schedule não pode ser nulo");
    }

    @Override
    public BigDecimal price(PricingRequest request) {
        Objects.requireNonNull(request, "request não pode ser nulo");
        return schedule.price(request.passType(), request.durationMinutes());
    }
}
