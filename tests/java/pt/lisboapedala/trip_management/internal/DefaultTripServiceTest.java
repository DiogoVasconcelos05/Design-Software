package pt.lisboapedala.trip_management.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.CREDENTIAL;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.DOCK_A;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.DOCK_B;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.T0;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.docked;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.released;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.DomainException;
import pt.lisboapedala.common.exception.EntityNotFoundException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.PassValidationStatus;
import pt.lisboapedala.trip_management.port.TripEventOutcome;
import pt.lisboapedala.trip_management.port.TripStatus;
import pt.lisboapedala.trip_management.port.TripView;

class DefaultTripServiceTest {

    @TempDir
    Path dir;
    TripTestFixture f;

    @BeforeEach
    void setUp() {
        f = new TripTestFixture(dir);
    }

    @Test
    void desbloqueioCriaViagemRequestedEEnviaComandoADoca() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);

        assertThat(trip.status()).isEqualTo(TripStatus.REQUESTED);
        assertThat(trip.requestedAt()).isEqualTo(T0);
        assertThat(f.openCommands).containsExactly(DOCK_A + "/" + trip.tripId());
        assertThat(f.service.getTrip(trip.tripId())).isEqualTo(trip);
    }

    @Test
    void passeInvalidoNaoEnviaNadaADoca() {
        f.validation = new PassValidation("user-1", PassValidationStatus.EXPIRED, null, "expirado");

        assertThatThrownBy(() -> f.service.requestUnlock(CREDENTIAL, DOCK_A))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PASS_INVALID));
        assertThat(f.openCommands).isEmpty();
        assertThat(f.service.history("user-1", 20, 0)).isEmpty();
    }

    @Test
    void utilizadorComViagemAtivaNaoPodeDesbloquearOutra() {
        f.service.requestUnlock(CREDENTIAL, DOCK_A);

        assertThatThrownBy(() -> f.service.requestUnlock(CREDENTIAL, DOCK_B))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void docaIndisponivelExpiraAViagemEPropagaOErro() {
        f.dockFailure = new BusinessRuleException(ErrorCode.DOCK_UNAVAILABLE, "doca offline");

        assertThatThrownBy(() -> f.service.requestUnlock(CREDENTIAL, DOCK_A)).isSameAs(f.dockFailure);
        assertThat(f.service.history("user-1", 20, 0))
                .singleElement().extracting(TripView::status).isEqualTo(TripStatus.EXPIRED);
    }

    @Test
    void bikeReleasedPoeAViagemEmCursoComOInstanteDaDoca() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.clock.advance(Duration.ofMinutes(5));

        DockEventRecord event = released(DOCK_A, T0.plusSeconds(10));
        assertThat(f.service.startTrip(trip.tripId(), event)).isEqualTo(TripEventOutcome.APPLIED);
        assertThat(f.service.startTrip(trip.tripId(), event)).isEqualTo(TripEventOutcome.DUPLICATE);

        TripView started = f.service.getTrip(trip.tripId());
        assertThat(started.status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isEqualTo(T0.plusSeconds(10));
        assertThat(started.bikeId()).isEqualTo(TripTestFixture.BIKE);
    }

    @Test
    void bikeReleasedDeOutraDocaOuViagemDesconhecidaNaoCorresponde() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);

        assertThat(f.service.startTrip(trip.tripId(), released(DOCK_B, T0))).isEqualTo(TripEventOutcome.UNMATCHED);
        assertThat(f.service.startTrip(UUID.randomUUID(), released(DOCK_A, T0)))
                .isEqualTo(TripEventOutcome.UNMATCHED);
        assertThat(f.service.getTrip(trip.tripId()).status()).isEqualTo(TripStatus.REQUESTED);
    }

    @Test
    void eventoComTipoErradoEhRecusado() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);

        assertThatThrownBy(() -> f.service.startTrip(trip.tripId(), docked(DOCK_A, T0)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
        assertThatThrownBy(() -> f.service.closeTrip(released(DOCK_A, T0)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void bikeReleasedTardioReativaViagemExpirada() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.clock.advance(Duration.ofMinutes(10));
        new TripExpiryJob(f.repository, f.clock, 60).expireStaleRequests();
        assertThat(f.service.getTrip(trip.tripId()).status()).isEqualTo(TripStatus.EXPIRED);

        // a doca estava sem rede e reenvia o evento (US-3): a bicicleta saiu mesmo
        assertThat(f.service.startTrip(trip.tripId(), released(DOCK_A, T0.plusSeconds(5))))
                .isEqualTo(TripEventOutcome.APPLIED);
        assertThat(f.service.getTrip(trip.tripId()).status()).isEqualTo(TripStatus.IN_PROGRESS);
    }

    @Test
    void fechoCalculaDuracaoECobraUmaVez() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.service.startTrip(trip.tripId(), released(DOCK_A, T0));

        assertThat(f.service.closeTrip(docked(DOCK_B, T0.plus(Duration.ofMinutes(31)))))
                .isEqualTo(TripEventOutcome.APPLIED);

        TripView closed = f.service.getTrip(trip.tripId());
        assertThat(closed.status()).isEqualTo(TripStatus.CLOSED);
        assertThat(closed.destinationDockId()).isEqualTo(DOCK_B);
        assertThat(closed.durationMinutes()).isEqualTo(31);
        assertThat(f.charges).singleElement().satisfies(s -> {
            assertThat(s.tripId()).isEqualTo(trip.tripId());
            assertThat(s.userId()).isEqualTo("user-1");
            assertThat(s.durationMinutes()).isEqualTo(31);
        });
        assertThat(f.repository.findPendingBilling(10)).isEmpty();
    }

    /** ADR-004, Verificação: o mesmo encaixe enviado 2 vezes resulta em 1 viagem fechada e 1 cobrança. */
    @Test
    void encaixeDuplicadoNaoDuplicaFechoNemCobranca() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.service.startTrip(trip.tripId(), released(DOCK_A, T0));
        DockEventRecord dock = docked(DOCK_B, T0.plus(Duration.ofMinutes(20)));

        assertThat(f.service.closeTrip(dock)).isEqualTo(TripEventOutcome.APPLIED);
        TripView afterFirst = f.service.getTrip(trip.tripId());
        assertThat(f.service.closeTrip(dock)).isEqualTo(TripEventOutcome.DUPLICATE);

        assertThat(f.service.getTrip(trip.tripId())).isEqualTo(afterFirst);
        assertThat(f.service.history("user-1", 20, 0)).hasSize(1);
        assertThat(f.charges).hasSize(1);
    }

    /** ADR-004, Verificação: um reenvio horas depois cobra o mesmo valor que o envio imediato. */
    @Test
    void reenvioTardioUsaOccurredAtENaoAHoraDeRececao() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.service.startTrip(trip.tripId(), released(DOCK_A, T0));
        f.clock.advance(Duration.ofHours(6));

        f.service.closeTrip(docked(DOCK_B, T0.plus(Duration.ofMinutes(14))));

        assertThat(f.charges).singleElement().satisfies(s -> {
            assertThat(s.durationMinutes()).isEqualTo(14);
            assertThat(s.endedAt()).isEqualTo(T0.plus(Duration.ofMinutes(14)));
        });
    }

    @Test
    void encaixeSemViagemEmCursoNaoCorresponde() {
        assertThat(f.service.closeTrip(docked(DOCK_A, T0))).isEqualTo(TripEventOutcome.UNMATCHED);
        assertThat(f.charges).isEmpty();
    }

    @Test
    void cobrancaFalhadaFicaNoOutboxEOJobReenvia() {
        f.paymentFailure = new BusinessRuleException(ErrorCode.PAYMENT_FAILED, "PSP em baixo");
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.service.startTrip(trip.tripId(), released(DOCK_A, T0));

        assertThat(f.service.closeTrip(docked(DOCK_B, T0.plusSeconds(90)))).isEqualTo(TripEventOutcome.APPLIED);
        assertThat(f.charges).isEmpty();
        assertThat(f.repository.findPendingBilling(10)).hasSize(1);

        f.paymentFailure = null;
        f.dispatcher.retryPendingBilling();

        assertThat(f.charges).singleElement().extracting(s -> s.durationMinutes()).isEqualTo(2L);
        assertThat(f.repository.findPendingBilling(10)).isEmpty();
    }

    @Test
    void expiraSoViagensRequestedForaDoPrazo() {
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        TripExpiryJob job = new TripExpiryJob(f.repository, f.clock, 60);

        f.clock.advance(Duration.ofSeconds(59));
        assertThat(job.expireStaleRequests()).isZero();
        f.clock.advance(Duration.ofSeconds(2));
        assertThat(job.expireStaleRequests()).isEqualTo(1);
        assertThat(f.service.getTrip(trip.tripId()).status()).isEqualTo(TripStatus.EXPIRED);

        // depois de expirar, o utilizador pode pedir novo desbloqueio
        assertThat(f.service.requestUnlock(CREDENTIAL, DOCK_B).status()).isEqualTo(TripStatus.REQUESTED);
    }

    @Test
    void historicoEhPaginadoDoMaisRecenteParaOMaisAntigo() {
        TripExpiryJob job = new TripExpiryJob(f.repository, f.clock, 60);
        TripView first = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.clock.advance(Duration.ofMinutes(5));
        job.expireStaleRequests();
        TripView second = f.service.requestUnlock(CREDENTIAL, DOCK_B);

        assertThat(f.service.history("user-1", 1, 0)).extracting(TripView::tripId).containsExactly(second.tripId());
        assertThat(f.service.history("user-1", 1, 1)).extracting(TripView::tripId).containsExactly(first.tripId());
        assertThatThrownBy(() -> f.service.history("user-1", 0, 0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> f.service.history("user-1", 20, -1)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void viagemInexistenteDaNotFound() {
        assertThatThrownBy(() -> f.service.getTrip(UUID.randomUUID()))
                .isInstanceOfSatisfying(EntityNotFoundException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
