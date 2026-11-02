package pt.lisboapedala.dock_gateway.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.DOCK_A;
import static pt.lisboapedala.dock_gateway.internal.DockGatewayTestFixture.T0;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.dock_gateway.persistence.ProcessedEventEntity;

class ProcessedEventPurgeJobTest {

    @TempDir
    Path dir;

    @Test
    void purgaSoOsEventosRecebidosHaMaisDe48Horas() {
        DockGatewayTestFixture f = new DockGatewayTestFixture(dir);
        UUID old = register(f, T0.minus(Duration.ofHours(49)));
        UUID recent = register(f, T0.minus(Duration.ofHours(47)));

        int purged = new ProcessedEventPurgeJob(f.repository, Clock.fixed(T0, ZoneOffset.UTC), 48).purgeExpired();

        assertThat(purged).isEqualTo(1);
        assertThat(f.repository.findById(old)).isEmpty();
        assertThat(f.repository.findById(recent)).isPresent();
    }

    private static UUID register(DockGatewayTestFixture f, Instant receivedAt) {
        UUID id = UUID.randomUUID();
        f.repository.registerIfNew(new ProcessedEventEntity(id, DOCK_A, DockEventType.BIKE_DOCKED, receivedAt, receivedAt));
        return id;
    }
}
