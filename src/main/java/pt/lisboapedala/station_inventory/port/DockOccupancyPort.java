package pt.lisboapedala.station_inventory.port;

import java.time.Instant;

/**
 * Porta de escrita publicada por station_inventory para o estado físico das docas (ARCHITECTURE.md §7.4.1–§7.4.2).
 * Usada por dock_gateway ao receber eventos das docas; a implementação é privada a station_inventory.
 *
 * <p>Todas as operações definem um estado (não incrementam contadores), por isso repeti-las é seguro.
 * O {@code occurredAt} é a hora da doca: a implementação pode ignorar uma atualização mais antiga do que a
 * última que aplicou à mesma doca (reenvios fora de ordem, US-3).
 */
public interface DockOccupancyPort {

    /** A bicicleta saiu da doca (BIKE_RELEASED). */
    void markDockFree(String dockId, Instant occurredAt);

    /** A bicicleta {@code bikeId} ficou encaixada na doca (BIKE_DOCKED). */
    void markDockOccupied(String dockId, String bikeId, Instant occurredAt);

    /** A doca ficou bloqueada ({@code locked = true}) ou desbloqueada para manutenção. */
    void markDockLocked(String dockId, boolean locked, Instant occurredAt);
}
