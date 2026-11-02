package pt.lisboapedala.maintenance_orders.port;

import java.util.Objects;

/**
 * Pedido de abertura de uma ordem de manutenção (corpo do endpoint 13 de ARCHITECTURE.md §7.3, mais a origem).
 *
 * @param bikeId      Bicicleta avariada.
 * @param faultType   Tipo de avaria; com {@code bikeId} identifica a ordem ativa (§7.4.3).
 * @param description Descrição livre da avaria.
 * @param reportedBy  Quem reportou (utilizador ou componente, ex.: {@code fleet_health}).
 * @param origin      Canal de entrada: automático (telemetria) ou reporte de utilizador.
 */
public record OpenMaintenanceOrder(
        String bikeId,
        FaultType faultType,
        String description,
        String reportedBy,
        MaintenanceOrigin origin
) {
    public OpenMaintenanceOrder {
        Objects.requireNonNull(bikeId, "bikeId não pode ser nulo");
        Objects.requireNonNull(faultType, "faultType não pode ser nulo");
        Objects.requireNonNull(origin, "origin não pode ser nulo");
    }
}
