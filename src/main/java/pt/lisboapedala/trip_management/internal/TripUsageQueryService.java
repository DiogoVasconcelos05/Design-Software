package pt.lisboapedala.trip_management.internal;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.trip_management.persistence.TripRepository;
import pt.lisboapedala.trip_management.query.TripUsageQuery;
import pt.lisboapedala.trip_management.query.TripUsageRecord;

/**
 * Implementação só de leitura de {@link TripUsageQuery} (ADR-005 §4).
 */
@Service
public class TripUsageQueryService implements TripUsageQuery {

    private final TripRepository trips;

    public TripUsageQueryService(TripRepository trips) {
        this.trips = trips;
    }

    @Override
    public List<TripUsageRecord> findClosedBetween(Instant from, Instant to, int limit, int offset) {
        Objects.requireNonNull(from, "from não pode ser nulo");
        Objects.requireNonNull(to, "to não pode ser nulo");
        if (!from.isBefore(to)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "from tem de ser anterior a to");
        }
        Paging.validate(limit, offset);
        return trips.findClosedBetween(from, to, limit, offset).stream()
                .map(Trip::fromEntity)
                .map(Trip::toUsageRecord)
                .toList();
    }
}
