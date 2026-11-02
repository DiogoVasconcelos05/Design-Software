package pt.lisboapedala.redistribution_planning.query;

import java.util.List;
import java.util.Objects;

/**
 * Representa a rota calculada para uma carrinha de redistribuição de bicicletas.
 *
 * @param vanId           Identificador da carrinha associada.
 * @param stops           Lista sequencial de paragens a realizar.
 * @param totalBikesMoved Número total estimado de bicicletas a movimentar nesta rota.
 */
public record VanRoute(
        String vanId,
        List<RouteStop> stops,
        int totalBikesMoved
) {
    public VanRoute {
        Objects.requireNonNull(vanId, "vanId não pode ser nulo");
        Objects.requireNonNull(stops, "stops não pode ser nulo");

        if (vanId.isBlank()) {
            throw new IllegalArgumentException("vanId não pode estar em branco");
        }
        stops = List.copyOf(stops);
    }
}