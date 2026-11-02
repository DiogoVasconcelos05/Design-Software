package pt.lisboapedala.municipal_reporting.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.lisboapedala.municipal_reporting.query.MonthlyReport;
import pt.lisboapedala.municipal_reporting.query.MonthlyReportQuery;
import pt.lisboapedala.municipal_reporting.query.ParishCo2Record;
import pt.lisboapedala.station_inventory.query.StationLocationRecord;
import pt.lisboapedala.station_inventory.query.StationOccupancyQuery;
import pt.lisboapedala.trip_management.query.TripUsageQuery;
import pt.lisboapedala.trip_management.query.TripUsageRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Serviço de aplicação responsável pela geração do relatório mensal municipal de utilização
 * e estimativa de CO2 evitado por freguesia (US-4 / Req. 7).
 *
 * Agrega em memória as viagens obtidas via {@link TripUsageQuery} e associa-as às freguesias
 * através de {@link StationOccupancyQuery#findAllLocations()}.
 */
@Service
public class MonthlyReportService implements MonthlyReportQuery {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);
    // TripUsageQuery aceita limit entre 1 e 100 (IMPLEMENTATION_NOTES, trip_management)
    private static final int PAGE_SIZE = 100;
    
    // Fator estimado de emissões evitadas: ~84 gramas de CO2 por km de bicicleta (0.084 kg/km)
    private static final BigDecimal KG_CO2_PER_KM = new BigDecimal("0.084");
    
    // Velocidade média estimada para cálculo de distância por tempo: 12 km/h (0.2 km/min)
    private static final BigDecimal KM_PER_MINUTE = new BigDecimal("0.2");

    private final TripUsageQuery tripUsageQuery;
    private final StationOccupancyQuery stationOccupancyQuery;
    private final Clock clock;

    public MonthlyReportService(
            TripUsageQuery tripUsageQuery,
            StationOccupancyQuery stationOccupancyQuery,
            Clock clock
    ) {
        this.tripUsageQuery = Objects.requireNonNull(tripUsageQuery, "tripUsageQuery não pode ser nulo");
        this.stationOccupancyQuery = Objects.requireNonNull(stationOccupancyQuery, "stationOccupancyQuery não pode ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock não pode ser nulo");
    }

    /**
     * Gera o relatório mensal municipal para um determinado ano/mês.
     *
     * @param yearMonth Mês a analisar (ex: 2026-10).
     * @return {@link MonthlyReport} contendo os totais agregados e o detalhe por freguesia.
     */
    @Override
    public MonthlyReport generate(YearMonth yearMonth) {
        Objects.requireNonNull(yearMonth, "yearMonth não pode ser nulo");
        log.info("A gerar relatório municipal para o mês: {}", yearMonth);

        // Define os limites temporais do mês em UTC
        Instant from = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // 1. Mapear identificadores de estação/doca -> freguesias via StationOccupancyQuery
        Map<String, String> locationToParishMap = stationOccupancyQuery.findAllLocations().stream()
                .collect(Collectors.toMap(
                        StationLocationRecord::stationId,
                        StationLocationRecord::parish,
                        (existing, replacement) -> existing
                ));

        // 2. Procurar todas as viagens no intervalo de forma paginada
        List<TripUsageRecord> monthTrips = fetchAllTripsForPeriod(from, to);

        // 3. Agregação em memória por freguesia de origem
        record ParishMetrics(long tripCount, BigDecimal distanceKm) {}

        Map<String, ParishMetrics> parishMetricsMap = new HashMap<>();

        for (TripUsageRecord trip : monthTrips) {
            String parish = locationToParishMap.getOrDefault(trip.originDockId(), "Desconhecida");
            BigDecimal dist = calculateEstimatedDistanceKm(trip.startedAt(), trip.endedAt());

            parishMetricsMap.compute(parish, (key, current) -> {
                if (current == null) {
                    return new ParishMetrics(1L, dist);
                }
                return new ParishMetrics(current.tripCount() + 1, current.distanceKm().add(dist));
            });
        }

        // 4. Construção do detalhe por freguesia e apuramento dos totais
        long totalTrips = 0;
        BigDecimal totalDistanceKm = BigDecimal.ZERO;
        List<ParishCo2Record> parishBreakdown = new ArrayList<>();

        for (Map.Entry<String, ParishMetrics> entry : parishMetricsMap.entrySet()) {
            String parish = entry.getKey();
            ParishMetrics metrics = entry.getValue();

            BigDecimal parishCo2 = metrics.distanceKm().multiply(KG_CO2_PER_KM).setScale(3, RoundingMode.HALF_UP);
            
            parishBreakdown.add(new ParishCo2Record(
                    parish,
                    metrics.tripCount(),
                    metrics.distanceKm().setScale(2, RoundingMode.HALF_UP),
                    parishCo2
            ));

            totalTrips += metrics.tripCount();
            totalDistanceKm = totalDistanceKm.add(metrics.distanceKm());
        }

        BigDecimal totalCo2AvoidedKg = totalDistanceKm.multiply(KG_CO2_PER_KM).setScale(3, RoundingMode.HALF_UP);

        log.info("Relatório municipal de {} gerado com sucesso: {} viagens, {} km percorridos.",
                yearMonth, totalTrips, totalDistanceKm);

        return new MonthlyReport(
                yearMonth,
                totalTrips,
                totalDistanceKm.setScale(2, RoundingMode.HALF_UP),
                totalCo2AvoidedKg,
                parishBreakdown
        );
    }

    private BigDecimal calculateEstimatedDistanceKm(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return BigDecimal.ZERO;
        }
        long minutes = Duration.between(startedAt, endedAt).toMinutes();
        return BigDecimal.valueOf(minutes).multiply(KM_PER_MINUTE);
    }

    private List<TripUsageRecord> fetchAllTripsForPeriod(Instant from, Instant to) {
        List<TripUsageRecord> allTrips = new ArrayList<>();
        int offset = 0;
        List<TripUsageRecord> page;

        do {
            page = tripUsageQuery.findClosedBetween(from, to, PAGE_SIZE, offset);
            allTrips.addAll(page);
            offset += PAGE_SIZE;
        } while (page.size() == PAGE_SIZE);

        return allTrips;
    }
}