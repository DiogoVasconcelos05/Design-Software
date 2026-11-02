package pt.lisboapedala.station_inventory.query;

import java.util.Objects;

/**
 * Visão imutável do estado de uma doca individual.
 */
public record DockStatusView(
        String dockId,
        String bikeId,
        String status
) {
    public DockStatusView {
        Objects.requireNonNull(dockId, "dockId não pode ser nulo");
        Objects.requireNonNull(status, "status não pode ser nulo");
    }
}