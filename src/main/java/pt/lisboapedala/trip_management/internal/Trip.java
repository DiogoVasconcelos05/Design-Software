package pt.lisboapedala.trip_management.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.TripSummary;
import pt.lisboapedala.trip_management.persistence.TripEntity;
import pt.lisboapedala.trip_management.port.TripStatus;
import pt.lisboapedala.trip_management.port.TripView;
import pt.lisboapedala.trip_management.query.TripUsageRecord;

/**
 * Viagem: máquina de estados imutável. Cada transição devolve uma nova instância e recusa estados de
 * partida inválidos. Os instantes vêm sempre do relógio injetado ou do occurredAt da doca (ADR-004 §5).
 */
final class Trip {

    private static final long MILLIS_PER_MINUTE = Duration.ofMinutes(1).toMillis();

    private final UUID id;
    private final String userId;
    private final PassType passType;
    private final TripStatus status;
    private final String originDockId;
    private final String destinationDockId;
    private final String bikeId;
    private final Instant requestedAt;
    private final Instant startedAt;
    private final Instant endedAt;
    private final UUID startEventId;
    private final UUID closeEventId;
    private final boolean billingDispatched;

    private Trip(UUID id, String userId, PassType passType, TripStatus status, String originDockId,
                 String destinationDockId, String bikeId, Instant requestedAt, Instant startedAt, Instant endedAt,
                 UUID startEventId, UUID closeEventId, boolean billingDispatched) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.passType = Objects.requireNonNull(passType);
        this.status = Objects.requireNonNull(status);
        this.originDockId = Objects.requireNonNull(originDockId);
        this.destinationDockId = destinationDockId;
        this.bikeId = bikeId;
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.startEventId = startEventId;
        this.closeEventId = closeEventId;
        this.billingDispatched = billingDispatched;
    }

    /** Nova viagem em REQUESTED para o titular de um passe válido. */
    static Trip request(UUID id, PassValidation validation, String dockId, Instant requestedAt) {
        if (!validation.isValid()) {
            throw new BusinessRuleException(ErrorCode.PASS_INVALID, "Passe inválido: " + validation.status());
        }
        return new Trip(id, validation.userId(), validation.passType(), TripStatus.REQUESTED, dockId,
                null, null, requestedAt, null, null, null, null, false);
    }

    /**
     * BIKE_RELEASED: passa a IN_PROGRESS. Aceita também EXPIRED, porque um evento reenviado após falha de
     * rede (US-3) prova que a bicicleta saiu mesmo depois de o prazo de desbloqueio ter passado.
     */
    Trip start(String bikeId, Instant startedAt, UUID eventId) {
        requireStatus(TripStatus.REQUESTED, TripStatus.EXPIRED);
        return new Trip(id, userId, passType, TripStatus.IN_PROGRESS, originDockId, null,
                Objects.requireNonNull(bikeId), requestedAt, Objects.requireNonNull(startedAt), null,
                Objects.requireNonNull(eventId), null, false);
    }

    /** BIKE_DOCKED: passa a CLOSED com a doca e o instante de encaixe. */
    Trip close(String dockId, Instant endedAt, UUID eventId) {
        requireStatus(TripStatus.IN_PROGRESS);
        return new Trip(id, userId, passType, TripStatus.CLOSED, originDockId, Objects.requireNonNull(dockId),
                bikeId, requestedAt, startedAt, Objects.requireNonNull(endedAt), startEventId,
                Objects.requireNonNull(eventId), false);
    }

    /** Desbloqueio que não se concretizou (doca indisponível ou sem BIKE_RELEASED no prazo). */
    Trip expire() {
        requireStatus(TripStatus.REQUESTED);
        return new Trip(id, userId, passType, TripStatus.EXPIRED, originDockId, null, null, requestedAt,
                null, null, null, null, false);
    }

    /** Duração em minutos, arredondada para cima; 0 se o relógio da doca der fim antes do início. */
    Long durationMinutes() {
        if (startedAt == null || endedAt == null) {
            return null;
        }
        long millis = Math.max(0, Duration.between(startedAt, endedAt).toMillis());
        return (millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE;
    }

    UUID id() {
        return id;
    }

    TripStatus status() {
        return status;
    }

    String originDockId() {
        return originDockId;
    }

    TripView toView() {
        return new TripView(id, userId, status, originDockId, destinationDockId, bikeId, requestedAt, startedAt,
                endedAt, durationMinutes());
    }

    TripSummary toSummary() {
        requireStatus(TripStatus.CLOSED);
        return new TripSummary(id, userId, passType, startedAt, endedAt, durationMinutes());
    }

    TripUsageRecord toUsageRecord() {
        requireStatus(TripStatus.CLOSED);
        return new TripUsageRecord(id, originDockId, destinationDockId, bikeId, startedAt, endedAt);
    }

    TripEntity toEntity() {
        return new TripEntity(id, userId, passType, status, originDockId, destinationDockId, bikeId, requestedAt,
                startedAt, endedAt, startEventId, closeEventId, billingDispatched);
    }

    static Trip fromEntity(TripEntity e) {
        return new Trip(e.tripId(), e.userId(), e.passType(), e.status(), e.originDockId(), e.destinationDockId(),
                e.bikeId(), e.requestedAt(), e.startedAt(), e.endedAt(), e.startEventId(), e.closeEventId(),
                e.billingDispatched());
    }

    private void requireStatus(TripStatus... allowed) {
        for (TripStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new BusinessRuleException(ErrorCode.CONFLICT,
                "A viagem " + id + " está em " + status + " e não permite esta operação.");
    }
}
