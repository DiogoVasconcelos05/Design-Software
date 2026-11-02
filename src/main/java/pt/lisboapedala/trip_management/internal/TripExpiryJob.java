package pt.lisboapedala.trip_management.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.lisboapedala.trip_management.persistence.TripRepository;

/**
 * Marca EXPIRED as viagens REQUESTED há mais de {@code lisboapedala.trip.unlock-timeout-seconds}
 * (ARCHITECTURE.md §7.4.1). Um UPDATE condicional torna o job seguro com várias réplicas.
 */
@Component
public class TripExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(TripExpiryJob.class);
    private static final long CHECK_INTERVAL_SECONDS = 15;

    private final TripRepository trips;
    private final Clock clock;
    private final Duration unlockTimeout;

    public TripExpiryJob(TripRepository trips, Clock clock,
                         @Value("${lisboapedala.trip.unlock-timeout-seconds}") long unlockTimeoutSeconds) {
        this.trips = trips;
        this.clock = clock;
        this.unlockTimeout = Duration.ofSeconds(unlockTimeoutSeconds);
    }

    @Scheduled(fixedDelay = CHECK_INTERVAL_SECONDS, initialDelay = CHECK_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    public int expireStaleRequests() {
        int expired = trips.expireRequestedBefore(Instant.now(clock).minus(unlockTimeout));
        if (expired > 0) {
            log.info("{} viagens REQUESTED expiraram sem BIKE_RELEASED", expired);
        }
        return expired;
    }
}
