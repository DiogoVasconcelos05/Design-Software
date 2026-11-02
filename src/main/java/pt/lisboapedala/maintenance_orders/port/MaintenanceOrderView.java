package pt.lisboapedala.maintenance_orders.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista imutável de uma ordem de manutenção (endpoints 13 e 14 de ARCHITECTURE.md §7.3).
 *
 * @param orderId      Identificador da ordem.
 * @param bikeId       Bicicleta avariada.
 * @param faultType    Tipo de avaria.
 * @param origin       Canal de entrada da avaria.
 * @param status       Estado atual.
 * @param description  Descrição da avaria.
 * @param reportedBy   Quem reportou.
 * @param technicianId Técnico atribuído; null enquanto {@code OPEN}.
 * @param openedAt     Instante de abertura.
 */
public record MaintenanceOrderView(
        UUID orderId,
        String bikeId,
        FaultType faultType,
        MaintenanceOrigin origin,
        MaintenanceOrderStatus status,
        String description,
        String reportedBy,
        String technicianId,
        Instant openedAt
) {
}
