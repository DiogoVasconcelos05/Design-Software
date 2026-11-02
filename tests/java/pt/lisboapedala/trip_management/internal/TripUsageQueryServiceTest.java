package pt.lisboapedala.trip_management.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.CREDENTIAL;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.DOCK_A;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.DOCK_B;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.T0;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.docked;
import static pt.lisboapedala.trip_management.internal.TripTestFixture.released;

import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.PassValidationStatus;
import pt.lisboapedala.trip_management.port.TripView;
import pt.lisboapedala.trip_management.query.TripUsageRecord;

class TripUsageQueryServiceTest {

    @TempDir
    Path dir;

    /** ADR-005, Verificação: TripUsageRecord não tem campos de utilizador nem de percurso. */
    @Test
    void registoDeUtilizacaoNaoTemDadosPessoais() {
        assertThat(Arrays.stream(TripUsageRecord.class.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(name -> name.toLowerCase().matches(".*(user|rider|route|path|gps|track|coordinate).*"));
    }

    @Test
    void devolveSoViagensFechadasNoPeriodo() {
        TripTestFixture f = new TripTestFixture(dir);
        TripView trip = f.service.requestUnlock(CREDENTIAL, DOCK_A);
        f.service.startTrip(trip.tripId(), released(DOCK_A, T0));
        f.validation = new PassValidation("user-2", PassValidationStatus.VALID, PassType.ANNUAL, null);
        f.service.requestUnlock(CREDENTIAL, DOCK_B); // fica REQUESTED: não entra no relatório
        Instant end = T0.plus(Duration.ofMinutes(12));
        f.service.closeTrip(docked(DOCK_B, end));
        TripUsageQueryService query = new TripUsageQueryService(f.repository);

        assertThat(query.findClosedBetween(T0, T0.plus(Duration.ofDays(1)), 20, 0))
                .containsExactly(new TripUsageRecord(trip.tripId(), DOCK_A, DOCK_B, TripTestFixture.BIKE, T0, end));
        assertThat(query.findClosedBetween(end.plusMillis(1), end.plus(Duration.ofDays(1)), 20, 0)).isEmpty();
        assertThat(query.findClosedBetween(T0, T0.plus(Duration.ofDays(1)), 20, 1)).isEmpty();
        assertThatThrownBy(() -> query.findClosedBetween(end, T0, 20, 0)).isInstanceOf(BusinessRuleException.class);
    }
}
