package pt.lisboapedala.trip_management.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.common.valueobject.PassValidation;
import pt.lisboapedala.common.valueobject.PassValidationStatus;
import pt.lisboapedala.trip_management.port.TripStatus;

class TripTest {

    private static final Instant T0 = Instant.parse("2026-11-01T17:40:00Z");
    private static final PassValidation VALID =
            new PassValidation("user-1", PassValidationStatus.VALID, PassType.OCCASIONAL, null);

    private static Trip inProgress() {
        return Trip.request(UUID.randomUUID(), VALID, "dock-A", T0).start("bike-1", T0, UUID.randomUUID());
    }

    @Test
    void cicloDeVidaCompleto() {
        Trip closed = inProgress().close("dock-B", T0.plus(Duration.ofMinutes(15)), UUID.randomUUID());

        assertThat(closed.status()).isEqualTo(TripStatus.CLOSED);
        assertThat(closed.toSummary().passType()).isEqualTo(PassType.OCCASIONAL);
        assertThat(closed.toSummary().durationMinutes()).isEqualTo(15);
    }

    @Test
    void duracaoArredondaParaCimaENuncaNegativa() {
        assertThat(inProgress().close("d", T0.plusSeconds(1), UUID.randomUUID()).durationMinutes()).isEqualTo(1);
        assertThat(inProgress().close("d", T0.plusSeconds(60), UUID.randomUUID()).durationMinutes()).isEqualTo(1);
        assertThat(inProgress().close("d", T0.plusSeconds(61), UUID.randomUUID()).durationMinutes()).isEqualTo(2);
        assertThat(inProgress().close("d", T0.minusSeconds(30), UUID.randomUUID()).durationMinutes()).isZero();
    }

    @Test
    void transicoesInvalidasSaoRecusadas() {
        Trip requested = Trip.request(UUID.randomUUID(), VALID, "dock-A", T0);
        Trip closed = inProgress().close("dock-B", T0, UUID.randomUUID());

        assertThatThrownBy(() -> requested.close("dock-B", T0, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> closed.start("bike-1", T0, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inProgress().expire()).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(requested::toSummary).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void passeInvalidoNaoCriaViagem() {
        PassValidation suspended = new PassValidation("user-1", PassValidationStatus.SUSPENDED, null, null);

        assertThatThrownBy(() -> Trip.request(UUID.randomUUID(), suspended, "dock-A", T0))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void conversaoParaEntidadeEhReversivel() {
        Trip closed = inProgress().close("dock-B", T0.plusSeconds(600), UUID.randomUUID());

        assertThat(Trip.fromEntity(closed.toEntity()).toEntity()).isEqualTo(closed.toEntity());
    }
}
