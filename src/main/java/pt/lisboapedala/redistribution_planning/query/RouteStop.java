package pt.lisboapedala.redistribution_planning.query;

import java.util.Objects;

/**
 * Representa uma paragem individual no itinerário de uma carrinha de reequilíbrio.
 * Cumpre a regra FF-06: utiliza String em vez de enum no pacote query.
 *
 * @param sequenceOrder Ordem da paragem na rota (1..N).
 * @param stationId     Identificador da estação de origem ou destino.
 * @param stationName   Nome amigável da estação.
 * @param action        Tipo de ação na paragem ("PICKUP" ou "DROP_OFF").
 * @param bikeCount     Quantidade de bicicletas a recolher ou entregar nesta paragem.
 */
public record RouteStop(
        int sequenceOrder,
        String stationId,
        String stationName,
        String action,
        int bikeCount
) {
    public static final String ACTION_PICKUP = "PICKUP";
    public static final String ACTION_DROP_OFF = "DROP_OFF";

    public RouteStop {
        Objects.requireNonNull(stationId, "stationId não pode ser nulo");
        Objects.requireNonNull(stationName, "stationName não pode ser nulo");
        Objects.requireNonNull(action, "action não pode ser nula");

        if (sequenceOrder <= 0) {
            throw new IllegalArgumentException("sequenceOrder deve ser superior a 0");
        }
        if (stationId.isBlank()) {
            throw new IllegalArgumentException("stationId não pode estar em branco");
        }
        if (bikeCount <= 0) {
            throw new IllegalArgumentException("bikeCount deve ser superior a 0");
        }
        if (!ACTION_PICKUP.equals(action) && !ACTION_DROP_OFF.equals(action)) {
            throw new IllegalArgumentException("action inválida: " + action);
        }
    }
}