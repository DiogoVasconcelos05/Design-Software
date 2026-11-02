package pt.lisboapedala.municipal_reporting.internal;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * DTO imutável de Java 21 do relatório mensal municipal de utilização e sustentabilidade (US-4).
 *
 * @param month                    Mês do relatório.
 * @param totalTrips               Total global de viagens no mês.
 * @param totalDistanceKm          Distância total percorrida em km.
 * @param totalCo2AvoidedKg        Total de CO2 evitado em kg.
 * @param parishBreakdown          Agregação por freguesia.
 */
public record MonthlyReport(
        YearMonth month,
        long totalTrips,
        BigDecimal totalDistanceKm,
        BigDecimal totalCo2AvoidedKg,
        List<ParishCo2Record> parishBreakdown
) {
    public MonthlyReport {
        Objects.requireNonNull(month, "month não pode ser nulo");
        Objects.requireNonNull(totalDistanceKm, "totalDistanceKm não pode ser nulo");
        Objects.requireNonNull(totalCo2AvoidedKg, "totalCo2AvoidedKg não pode ser nulo");
        Objects.requireNonNull(parishBreakdown, "parishBreakdown não pode ser nulo");

        if (totalTrips < 0) {
            throw new IllegalArgumentException("totalTrips não pode ser negativo");
        }
        parishBreakdown = List.copyOf(parishBreakdown);
    }
}