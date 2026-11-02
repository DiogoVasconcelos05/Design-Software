package pt.lisboapedala.tariff_pricing.port;

import java.util.Objects;
import pt.lisboapedala.common.valueobject.PassType;

/**
 * Pedido tipado de cálculo tarifário (docs/02 Conascência 3: nome e tipo em vez de posição e significado).
 *
 * @param passType        Tipo de passe do utilizador.
 * @param durationMinutes Duração da viagem em minutos, já arredondada para cima por trip_management.
 */
public record PricingRequest(PassType passType, long durationMinutes) {

    public PricingRequest {
        Objects.requireNonNull(passType, "passType não pode ser nulo");
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes não pode ser negativo");
        }
    }
}
