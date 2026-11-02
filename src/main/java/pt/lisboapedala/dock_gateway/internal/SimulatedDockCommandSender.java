package pt.lisboapedala.dock_gateway.internal;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.trip_management.port.DockCommandPort;

/**
 * Implementação de {@link DockCommandPort} (US-1, passo 2) para docas simuladas (ARCHITECTURE.md §0.3).
 * O envio é assíncrono: o comando só é registado no log, e a resposta chega mais tarde como evento
 * BIKE_RELEASED com {@code commandId = tripId}. Sem cliente HTTP (FF-14a).
 */
@Component
public class SimulatedDockCommandSender implements DockCommandPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedDockCommandSender.class);

    @Override
    public void sendOpenCommand(String dockId, UUID tripId) {
        if (dockId == null || dockId.isBlank() || tripId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "dockId e tripId são obrigatórios");
        }
        log.info("Comando de abertura (simulado) enviado à doca {} com commandId {}", dockId, tripId);
    }
}
