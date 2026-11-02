package pt.lisboapedala.dock_gateway.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.lisboapedala.dock_gateway.persistence.ProcessedEventRepository;

/**
 * Purga os eventId registados há mais de {@code lisboapedala.dock.processed-event-retention-hours}
 * (ADR-004 §2: 48 h = buffer de 24 h da doca mais margem). Um DELETE por instante é seguro com várias réplicas.
 */
@Component
public class ProcessedEventPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventPurgeJob.class);
    private static final long PURGE_INTERVAL_MINUTES = 60;

    private final ProcessedEventRepository processedEvents;
    private final Clock clock;
    private final Duration retention;

    public ProcessedEventPurgeJob(ProcessedEventRepository processedEvents, Clock clock,
                                  @Value("${lisboapedala.dock.processed-event-retention-hours}") long retentionHours) {
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.retention = Duration.ofHours(retentionHours);
    }

    @Scheduled(fixedDelay = PURGE_INTERVAL_MINUTES, initialDelay = PURGE_INTERVAL_MINUTES, timeUnit = TimeUnit.MINUTES)
    public int purgeExpired() {
        int purged = processedEvents.purgeReceivedBefore(Instant.now(clock).minus(retention));
        if (purged > 0) {
            log.info("{} eventId de docas purgados (retenção de {})", purged, retention);
        }
        return purged;
    }
}
