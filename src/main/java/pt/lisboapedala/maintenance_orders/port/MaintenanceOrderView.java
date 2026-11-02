package pt.lisboapedala.maintenance_orders.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista imutável de uma ordem de manutenção (endpoints 13 a 17 de ARCHITECTURE.md §7.3).
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
 * @param startedAt    Instante em que a reparação começou; null antes de {@code IN_PROGRESS}.
 * @param completedAt  Instante de conclusão; null antes de {@code COMPLETED}.
 * @param notes        Notas do técnico na conclusão; null antes de {@code COMPLETED}.
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
        Instant openedAt,
        Instant startedAt,
        Instant completedAt,
        String notes
) {
}
