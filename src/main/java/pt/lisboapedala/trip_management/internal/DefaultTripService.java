package pt.lisboapedala.trip_management.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.EntityNotFoundException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.identity_access.port.CredentialValidator;
import pt.lisboapedala.trip_management.persistence.TripEntity;
import pt.lisboapedala.trip_management.persistence.TripRepository;
import pt.lisboapedala.trip_management.port.DockCommandPort;
import pt.lisboapedala.trip_management.port.TripEventOutcome;
import pt.lisboapedala.trip_management.port.TripService;
import pt.lisboapedala.trip_management.port.TripStatus;
import pt.lisboapedala.trip_management.port.TripView;

/**
 * Orquestra o ciclo de vida da viagem (ARCHITECTURE.md §4 e §7.4.1–§7.4.2).
 */
@Service
public class DefaultTripService implements TripService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTripService.class);

    private final TripRepository trips;
    private final CredentialValidator credentialValidator;
    private final DockCommandPort dockCommands;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DefaultTripService(TripRepository trips, CredentialValidator credentialValidator,
                              DockCommandPort dockCommands, ApplicationEventPublisher events, Clock clock) {
        this.trips = trips;
        this.credentialValidator = credentialValidator;
        this.dockCommands = dockCommands;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Ordem imposta pelo acoplamento temporal (ARCHITECTURE.md §4): validar o passe, gravar a viagem e só
     * depois pedir a abertura. Sem transação envolvente, para que a viagem esteja gravada antes de a doca
     * poder responder com BIKE_RELEASED.
     */
    @Override
    public TripView requestUnlock(Credential credential, String dockId) {
        Objects.requireNonNull(credential, "credential não pode ser nulo");
        requireText(dockId, "dockId");

        PassValidation validation = credentialValidator.validateCredential(credential);
        if (!validation.isValid()) {
            throw new BusinessRuleException(ErrorCode.PASS_INVALID,
                    "Passe inválido (" + validation.status() + "): nada foi enviado à doca.");
        }
        if (trips.hasActiveTrip(validation.userId())) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "O utilizador já tem uma viagem ativa.");
        }

        Trip trip = Trip.request(UUID.randomUUID(), validation, dockId, Instant.now(clock));
        trips.insert(trip.toEntity());
        try {
            dockCommands.sendOpenCommand(dockId, trip.id());
        } catch (RuntimeException e) {
            trips.update(trip.expire().toEntity(), TripStatus.REQUESTED);
            log.warn("Comando de abertura falhou para a viagem {} na doca {}", trip.id(), dockId, e);
            throw e;
        }
        log.info("Viagem {} pedida na doca {}", trip.id(), dockId);
        return trip.toView();
    }

    @Override
    public TripView getTrip(UUID tripId) {
        Objects.requireNonNull(tripId, "tripId não pode ser nulo");
        return trips.findById(tripId)
                .map(Trip::fromEntity)
                .map(Trip::toView)
                .orElseThrow(() -> new EntityNotFoundException("Viagem", tripId));
    }

    @Override
    public List<TripView> history(String userId, int limit, int offset) {
        requireText(userId, "userId");
        Paging.validate(limit, offset);
        return trips.findByUser(userId, limit, offset).stream()
                .map(Trip::fromEntity)
                .map(Trip::toView)
                .toList();
    }

    @Override
    @Transactional
    public TripEventOutcome startTrip(UUID tripId, DockEventRecord event) {
        Objects.requireNonNull(tripId, "tripId não pode ser nulo");
        requireType(event, DockEventType.BIKE_RELEASED);

        if (trips.findByStartEventId(event.eventId()).isPresent()) {
            return TripEventOutcome.DUPLICATE;
        }
        Optional<Trip> found = trips.findById(tripId).map(Trip::fromEntity);
        if (found.isEmpty()) {
            log.warn("BIKE_RELEASED {} sem viagem {}", event.eventId(), tripId);
            return TripEventOutcome.UNMATCHED;
        }
        Trip trip = found.get();
        if (!trip.originDockId().equals(event.dockId())
                || !(trip.status() == TripStatus.REQUESTED || trip.status() == TripStatus.EXPIRED)) {
            log.warn("BIKE_RELEASED {} não corresponde à viagem {} ({}, doca {})",
                    event.eventId(), tripId, trip.status(), trip.originDockId());
            return TripEventOutcome.UNMATCHED;
        }

        Trip started = trip.start(event.bikeId(), event.occurredAt(), event.eventId());
        if (!trips.update(started.toEntity(), trip.status())) {
            return resolveRace(trips.findByStartEventId(event.eventId()));
        }
        log.info("Viagem {} em curso com a bicicleta {}", tripId, event.bikeId());
        return TripEventOutcome.APPLIED;
    }

    /**
     * Idempotente pelo eventId do encaixe (ADR-004 §3). Junta-se à transação do dock_gateway; a cobrança
     * é pedida pelo {@link TripBillingDispatcher} só depois do commit (ADR-004 §4, FF-15).
     */
    @Override
    @Transactional
    public TripEventOutcome closeTrip(DockEventRecord event) {
        requireType(event, DockEventType.BIKE_DOCKED);

        if (trips.findByCloseEventId(event.eventId()).isPresent()) {
            return TripEventOutcome.DUPLICATE;
        }
        Optional<TripEntity> inProgress = trips.findInProgressByBike(event.bikeId());
        if (inProgress.isEmpty()) {
            log.warn("BIKE_DOCKED {} sem viagem em curso para a bicicleta {}", event.eventId(), event.bikeId());
            return TripEventOutcome.UNMATCHED;
        }

        Trip closed = Trip.fromEntity(inProgress.get()).close(event.dockId(), event.occurredAt(), event.eventId());
        if (!trips.update(closed.toEntity(), TripStatus.IN_PROGRESS)) {
            return resolveRace(trips.findByCloseEventId(event.eventId()));
        }
        events.publishEvent(new TripClosedEvent(closed.toSummary()));
        log.info("Viagem {} fechada na doca {} ({} min)", closed.id(), event.dockId(), closed.durationMinutes());
        return TripEventOutcome.APPLIED;
    }

    /** Outra operação mudou a viagem entre a leitura e a escrita: duplicado se foi o mesmo evento. */
    private static TripEventOutcome resolveRace(Optional<TripEntity> sameEvent) {
        return sameEvent.isPresent() ? TripEventOutcome.DUPLICATE : TripEventOutcome.UNMATCHED;
    }

    private static void requireType(DockEventRecord event, DockEventType expected) {
        Objects.requireNonNull(event, "event não pode ser nulo");
        if (event.eventType() != expected) {
            throw new BusinessRuleException(ErrorCode.VALIDATION,
                    "Esperado evento " + expected + ", recebido " + event.eventType());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, name + " é obrigatório");
        }
    }
}
