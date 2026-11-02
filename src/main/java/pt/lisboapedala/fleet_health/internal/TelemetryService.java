package pt.lisboapedala.fleet_health.internal;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.fleet_health.persistence.ActiveAnomalyEntity;
import pt.lisboapedala.fleet_health.persistence.ActiveAnomalyRepository;
import pt.lisboapedala.fleet_health.port.TelemetryIngestion;
import pt.lisboapedala.fleet_health.port.TelemetryReading;

/**
 * Entrada de telemetria das elétricas (US-2, ARCHITECTURE.md §7.4.3). Aplica as {@link AnomalyRule} à leitura e
 * compara o resultado com as anomalias ativas da bicicleta: só as novas abrem ordem de manutenção, e as que
 * deixaram de aparecer ficam resolvidas. Sem transação própria: uma leitura que não muda nada (o caso comum)
 * faz apenas uma leitura indexada, e só as mudanças passam pelo {@link AnomalyRecorder}.
 */
@Service
public class TelemetryService implements TelemetryIngestion {

    private final List<AnomalyRule> rules;
    private final ActiveAnomalyRepository anomalies;
    private final AnomalyRecorder recorder;

    public TelemetryService(List<AnomalyRule> rules, ActiveAnomalyRepository anomalies, AnomalyRecorder recorder) {
        if (rules.stream().map(AnomalyRule::type).distinct().count() != rules.size()) {
            throw new IllegalArgumentException("Há mais de uma regra para o mesmo tipo de anomalia: " + rules);
        }
        this.rules = List.copyOf(rules);
        this.anomalies = anomalies;
        this.recorder = recorder;
    }

    @Override
    public void ingest(TelemetryReading reading) {
        validate(reading);
        List<Anomaly> detected = rules.stream()
                .map(rule -> rule.evaluate(reading))
                .flatMap(Optional::stream)
                .toList();
        Set<String> detectedTypes = detected.stream().map(a -> a.type().name()).collect(Collectors.toSet());
        List<ActiveAnomalyEntity> active = anomalies.findByBike(reading.bikeId());
        Set<String> activeTypes = active.stream().map(ActiveAnomalyEntity::anomalyType).collect(Collectors.toSet());

        List<Anomaly> fresh = detected.stream().filter(a -> !activeTypes.contains(a.type().name())).toList();
        // Uma leitura mais antiga do que a deteção (reenvio fora de ordem) não resolve a anomalia.
        List<String> resolved = active.stream()
                .filter(a -> !detectedTypes.contains(a.anomalyType()))
                .filter(a -> a.detectedAt().isBefore(reading.occurredAt()))
                .map(ActiveAnomalyEntity::anomalyType)
                .toList();
        if (!fresh.isEmpty() || !resolved.isEmpty()) {
            recorder.record(reading.bikeId(), reading.occurredAt(), fresh, resolved);
        }
    }

    private static void validate(TelemetryReading reading) {
        if (reading == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "A leitura de telemetria é obrigatória");
        }
        if (reading.bikeId() == null || reading.bikeId().isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "O bikeId da leitura é obrigatório");
        }
        if (reading.occurredAt() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "O occurredAt da leitura é obrigatório");
        }
        if (!Double.isFinite(reading.batteryTemperatureCelsius())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION, "A temperatura da bateria tem de ser um número");
        }
    }
}
