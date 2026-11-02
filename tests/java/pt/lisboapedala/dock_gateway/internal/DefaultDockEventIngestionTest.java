package pt.lisboapedala.dock_gateway.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.BIKE;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.DOCK_A;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.T0;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.event;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.lisboapedala.common.event.DockEventRecord;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.common.exception.DomainException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.dock_gateway.port.DockEventSubmission;
import pt.lisboapedala.dock_gateway.port.IngestOutcome;
import pt.lisboapedala.trip_management.port.TripEventOutcome;

class DefaultDockEventIngestionTest {

    @TempDir
    Path dir;
    DockGatewayTestFixture f;

    @BeforeEach
    void setUp() {
        f = new DockGatewayTestFixture(dir);
    }

    @Test
    void bikeReleasedIniciaAViagemDoComandoELibertaADoca() {
        UUID tripId = UUID.randomUUID();
        DockEventRecord released = event(DockEventType.BIKE_RELEASED, T0);

        IngestOutcome outcome = f.ingestion.ingest(new DockEventSubmission(released, tripId));

        assertThat(outcome).isEqualTo(IngestOutcome.PROCESSED);
        assertThat(f.trips.calls).containsExactly("start " + tripId + " " + released.eventId());
        assertThat(f.docks.calls).containsExactly("free " + DOCK_A);
        assertThat(f.repository.findById(released.eventId())).hasValueSatisfying(e -> {
            assertThat(e.occurredAt()).isEqualTo(T0);
            assertThat(e.receivedAt()).isEqualTo(T0);
        });
    }

    @Test
    void bikeDockedOcupaADocaAntesDeFecharAViagem() {
        DockEventRecord docked = event(DockEventType.BIKE_DOCKED, T0);

        assertThat(f.ingestion.ingest(new DockEventSubmission(docked, null))).isEqualTo(IngestOutcome.PROCESSED);
        assertThat(f.docks.calls).containsExactly("occupied " + DOCK_A + " " + BIKE);
        assertThat(f.trips.calls).containsExactly("close " + docked.eventId());
    }

    @Test
    void eventoRepetidoEDescartadoSemEfeitos() {
        DockEventSubmission docked = new DockEventSubmission(event(DockEventType.BIKE_DOCKED, T0), null);

        f.ingestion.ingest(docked);
        IngestOutcome second = f.ingestion.ingest(docked);

        assertThat(second).isEqualTo(IngestOutcome.DUPLICATE);
        assertThat(f.trips.calls).hasSize(1);
        assertThat(f.docks.calls).hasSize(1);
    }

    @Test
    void duplicadoDetetadoPorTripManagementTambemEDuplicadoParaADoca() {
        f.trips.outcome = TripEventOutcome.DUPLICATE;

        assertThat(f.ingestion.ingest(new DockEventSubmission(event(DockEventType.BIKE_DOCKED, T0), null)))
                .isEqualTo(IngestOutcome.DUPLICATE);
    }

    @Test
    void semViagemCorrespondenteOEventoFicaUnmatchedMasADocaEAtualizada() {
        f.trips.outcome = TripEventOutcome.UNMATCHED;

        assertThat(f.ingestion.ingest(new DockEventSubmission(event(DockEventType.BIKE_DOCKED, T0), null)))
                .isEqualTo(IngestOutcome.UNMATCHED);
        assertThat(f.docks.calls).hasSize(1);
    }

    @Test
    void bikeReleasedSemCommandIdNaoChamaTripManagement() {
        assertThat(f.ingestion.ingest(new DockEventSubmission(event(DockEventType.BIKE_RELEASED, T0), null)))
                .isEqualTo(IngestOutcome.UNMATCHED);
        assertThat(f.trips.calls).isEmpty();
        assertThat(f.docks.calls).containsExactly("free " + DOCK_A);
    }

    @Test
    void eventosDeManutencaoBloqueiamEDesbloqueiamADoca() {
        f.ingestion.ingest(new DockEventSubmission(event(DockEventType.DOCK_MAINTENANCE_LOCKED, T0), null));
        f.ingestion.ingest(new DockEventSubmission(event(DockEventType.DOCK_MAINTENANCE_UNLOCKED, T0), null));

        assertThat(f.docks.calls).containsExactly("locked " + DOCK_A, "unlocked " + DOCK_A);
        assertThat(f.trips.calls).isEmpty();
    }

    @Test
    void loteEAplicadoPorOrdemDeOccurredAtEDevolveResultadosPelaOrdemRecebida() {
        UUID tripId = UUID.randomUUID();
        DockEventRecord released = event(DockEventType.BIKE_RELEASED, T0);
        DockEventRecord docked = event(DockEventType.BIKE_DOCKED, T0.plus(Duration.ofMinutes(20)));
        f.trips.outcome = TripEventOutcome.APPLIED;

        List<IngestOutcome> outcomes = f.ingestion.ingestBatch(List.of(
                new DockEventSubmission(docked, null),
                new DockEventSubmission(released, tripId),
                new DockEventSubmission(docked, null)));

        assertThat(outcomes).containsExactly(IngestOutcome.PROCESSED, IngestOutcome.PROCESSED, IngestOutcome.DUPLICATE);
        assertThat(f.trips.calls).containsExactly(
                "start " + tripId + " " + released.eventId(), "close " + docked.eventId());
    }

    @Test
    void loteVazioComNulosOuDemasiadoGrandeEInvalido() {
        List<DockEventSubmission> withNull = new ArrayList<>();
        withNull.add(null);
        List<DockEventSubmission> tooBig = Collections.nCopies(DefaultDockEventIngestion.MAX_BATCH_SIZE + 1,
                new DockEventSubmission(event(DockEventType.BIKE_DOCKED, T0), null));

        for (List<DockEventSubmission> batch : List.of(List.<DockEventSubmission>of(), withNull, tooBig)) {
            assertThatThrownBy(() -> f.ingestion.ingestBatch(batch))
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
        }
        assertThat(f.trips.calls).isEmpty();
    }

    @Test
    void submissaoNulaEInvalida() {
        assertThatThrownBy(() -> f.ingestion.ingest(null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
    }
}
