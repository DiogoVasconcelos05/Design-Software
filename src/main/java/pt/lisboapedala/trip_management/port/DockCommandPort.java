package pt.lisboapedala.trip_management.port;

import java.util.UUID;

/**
 * Porta de saída de trip_management para o envio do sinal físico de abertura (US-1, passo 2).
 * É definida aqui e implementada por dock_gateway, porque dock_gateway pode usar trip_management
 * mas não o contrário (docs/03, regras allowed-to-use; sem ciclos).
 */
public interface DockCommandPort {

    /**
     * Envia de forma assíncrona o comando de abertura; {@code tripId} segue como commandId e volta no
     * evento BIKE_RELEASED. Lança {@code DOCK_UNAVAILABLE} se o comando não puder ser aceite.
     */
    void sendOpenCommand(String dockId, UUID tripId);
}
