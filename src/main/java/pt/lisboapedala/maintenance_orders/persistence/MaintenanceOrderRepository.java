package pt.lisboapedala.maintenance_orders.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;

/**
 * Acesso à tabela {@code maintenance_orders}. Privado a maintenance_orders.
 */
public interface MaintenanceOrderRepository {

    /**
     * Insere a ordem se a bicicleta não tiver já uma ordem ativa para o mesmo tipo de avaria; a unicidade é
     * garantida pelo índice único parcial {@code ux_maintenance_orders_active}.
     *
     * @return false se já havia uma ordem ativa (outro pedido ou réplica abriu-a primeiro)
     */
    boolean insertIfNoActive(MaintenanceOrderEntity order);

    /**
     * Grava a transição, mas só se a ordem ainda estiver em {@code expectedStatus}.
     *
     * @return false se outro pedido mudou a ordem entretanto
     */
    boolean update(MaintenanceOrderEntity order, MaintenanceOrderStatus expectedStatus);

    Optional<MaintenanceOrderEntity> findById(UUID orderId);

    Optional<MaintenanceOrderEntity> findActive(String bikeId, FaultType faultType);

    /** Ordens nos estados pedidos, por ordem de abertura. */
    List<MaintenanceOrderEntity> findByStatus(List<MaintenanceOrderStatus> statuses);
}
