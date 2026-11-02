package pt.lisboapedala.trip_management.port;

/**
 * Estados da máquina de estados da viagem.
 * REQUESTED -> IN_PROGRESS -> CLOSED; REQUESTED -> EXPIRED (sem BIKE_RELEASED dentro do prazo).
 */
public enum TripStatus {
    REQUESTED,
    IN_PROGRESS,
    CLOSED,
    EXPIRED;

    public boolean isActive() {
        return this == REQUESTED || this == IN_PROGRESS;
    }
}
