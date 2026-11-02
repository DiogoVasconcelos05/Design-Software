package pt.lisboapedala.maintenance_orders.persistence;

import java.time.Instant;
import java.util.UUID;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;

/**
 * Linha da tabela {@code maintenance_orders}. Privada a maintenance_orders.
 */
public record MaintenanceOrderEntity(
        UUID orderId,
        String bikeId,
        FaultType faultType,
        MaintenanceOrigin origin,
        MaintenanceOrderStatus status,
        String description,
        String reportedBy,
        String technicianId,
        String notes,
        Instant openedAt,
        Instant startedAt,
        Instant completedAt
) {
}
