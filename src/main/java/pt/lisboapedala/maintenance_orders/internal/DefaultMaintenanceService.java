package pt.lisboapedala.maintenance_orders.internal;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.EntityNotFoundException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.maintenance_orders.persistence.MaintenanceOrderRepository;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderView;
import pt.lisboapedala.maintenance_orders.port.MaintenanceService;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

/**
 * Implementação de {@link MaintenanceService} (US-2, ARCHITECTURE.md §7.3 e §7.4.3). Cada operação é uma
 * transação local; {@link #open} junta-se à transação de quem chama (ex.: o registo da anomalia em
 * fleet_health), para que a anomalia e a ordem fiquem gravadas juntas ou nenhuma.
 */
@Service
public class DefaultMaintenanceService implements MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMaintenanceService.class);
    private static final List<MaintenanceOrderStatus> ACTIVE = Arrays.stream(MaintenanceOrderStatus.values())
            .filter(s -> s != MaintenanceOrderStatus.COMPLETED)
            .toList();

    private final MaintenanceOrderRepository orders;
    private final Clock clock;

    public DefaultMaintenanceService(MaintenanceOrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MaintenanceOrderView open(OpenMaintenanceOrder command) {
        validate(command);
        MaintenanceOrder order = MaintenanceOrder.open(UUID.randomUUID(), command, clock.instant());
        if (orders.insertIfNoActive(order.toEntity())) {
            log.info("Ordem de manutenção {} aberta para a bicicleta {} ({}, {})",
                    order.id(), command.bikeId(), command.faultType(), command.origin());
            return order.toView();
        }
        // §7.4.3: já há uma ordem ativa para (bikeId, faultType); devolve-a em vez de criar outra.
        return orders.findActive(command.bikeId(), command.faultType())
                .map(MaintenanceOrder::fromEntity)
                .map(MaintenanceOrder::toView)
                .orElseThrow(() -> new IllegalStateException("Ordem ativa de " + command.bikeId() + "/"
                        + command.faultType() + " recusada na inserção mas não encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceOrderView> list(MaintenanceOrderStatus status) {
        List<MaintenanceOrderStatus> statuses = status == null ? ACTIVE : List.of(status);
        return orders.findByStatus(statuses).stream()
                .map(MaintenanceOrder::fromEntity)
                .map(MaintenanceOrder::toView)
                .toList();
    }

    @Override
    @Transactional
    public MaintenanceOrderView assign(UUID orderId, String technicianId) {
        requireText(technicianId, "O technicianId é obrigatório");
        return transition(orderId, order -> order.assign(technicianId));
    }

    @Override
    @Transactional
    public MaintenanceOrderView start(UUID orderId) {
        return transition(orderId, order -> order.start(clock.instant()));
    }

    @Override
    @Transactional
    public MaintenanceOrderView complete(UUID orderId, String notes) {
        return transition(orderId, order -> order.complete(notes, clock.instant()));
    }

    /** Aplica a transição e grava-a só se a ordem não tiver mudado entretanto (outro pedido ou réplica). */
    private MaintenanceOrderView transition(UUID orderId, UnaryOperator<MaintenanceOrder> change) {
        if (orderId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "O orderId é obrigatório");
        }
        MaintenanceOrder current = orders.findById(orderId)
                .map(MaintenanceOrder::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException("Ordem de manutenção", orderId));
        MaintenanceOrder next = change.apply(current);
        if (next == current) {
            return current.toView();
        }
        if (!orders.update(next.toEntity(), current.status())) {
            throw new BusinessRuleException(ErrorCode.CONFLICT,
                    "A ordem de manutenção " + orderId + " foi alterada por outro pedido; tente novamente.");
        }
        log.info("Ordem de manutenção {}: {} -> {}", orderId, current.status(), next.status());
        return next.toView();
    }

    private static void validate(OpenMaintenanceOrder command) {
        if (command == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "O pedido de abertura é obrigatório");
        }
        requireText(command.bikeId(), "O bikeId é obrigatório");
        requireText(command.reportedBy(), "O reportedBy é obrigatório");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, message);
        }
    }
}
