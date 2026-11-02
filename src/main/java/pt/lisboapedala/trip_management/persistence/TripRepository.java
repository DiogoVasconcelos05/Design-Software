package pt.lisboapedala.trip_management.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.lisboapedala.trip_management.port.TripStatus;

/**
 * Acesso à tabela {@code trips}. Privado a trip_management.
 */
public interface TripRepository {

    void insert(TripEntity trip);

    /**
     * Grava o novo estado da viagem só se o estado atual for {@code expectedStatus} (controlo otimista).
     *
     * @return false se outra operação mudou a viagem entretanto
     */
    boolean update(TripEntity trip, TripStatus expectedStatus);

    Optional<TripEntity> findById(UUID tripId);

    Optional<TripEntity> findByStartEventId(UUID eventId);

    Optional<TripEntity> findByCloseEventId(UUID eventId);

    Optional<TripEntity> findInProgressByBike(String bikeId);

    boolean hasActiveTrip(String userId);

    /** Histórico do utilizador, mais recente primeiro. */
    List<TripEntity> findByUser(String userId, int limit, int offset);

    /** Marca EXPIRED as viagens REQUESTED pedidas antes de {@code cutoff}; devolve quantas mudaram. */
    int expireRequestedBefore(Instant cutoff);

    /** Viagens CLOSED cuja cobrança ainda não foi entregue a payment_invoicing (outbox). */
    List<TripEntity> findPendingBilling(int limit);

    void markBillingDispatched(UUID tripId);

    List<TripEntity> findClosedBetween(Instant from, Instant to, int limit, int offset);
}
