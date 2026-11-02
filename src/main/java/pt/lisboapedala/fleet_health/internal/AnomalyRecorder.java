package pt.lisboapedala.fleet_health.internal;

import java.time.Instant;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.lisboapedala.fleet_health.persistence.ActiveAnomalyEntity;
import pt.lisboapedala.fleet_health.persistence.ActiveAnomalyRepository;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;
import pt.lisboapedala.maintenance_orders.port.MaintenanceService;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

/**
 * Unidade de trabalho de uma mudança no estado de saúde de uma bicicleta (ARCHITECTURE.md §7.4.3): registar
 * as anomalias novas e abrir as respetivas ordens numa única transação local. Se a abertura falhar, a anomalia
 * não fica registada e a leitura seguinte (30 s depois) volta a tentar. Só corre quando o estado muda.
 */
@Component
public class AnomalyRecorder {

    static final String REPORTED_BY = "fleet_health";

    private static final Logger log = LoggerFactory.getLogger(AnomalyRecorder.class);

    private final ActiveAnomalyRepository anomalies;
    private final MaintenanceService maintenance;

    public AnomalyRecorder(ActiveAnomalyRepository anomalies, MaintenanceService maintenance) {
        this.anomalies = anomalies;
        this.maintenance = maintenance;
    }

    @Transactional
    public void record(String bikeId, Instant occurredAt, Collection<Anomaly> detected, Collection<String> resolved) {
        for (String anomalyType : resolved) {
            if (anomalies.clearIfDetectedBefore(bikeId, anomalyType, occurredAt)) {
                log.info("Anomalia {} da bicicleta {} resolvida", anomalyType, bikeId);
            }
        }
        for (Anomaly anomaly : detected) {
            // Uma anomalia já registada (por outra réplica, por exemplo) já tem ordem aberta.
            if (anomalies.registerIfNew(new ActiveAnomalyEntity(bikeId, anomaly.type().name(), occurredAt))) {
                maintenance.open(new OpenMaintenanceOrder(bikeId, anomaly.type().faultType(),
                        anomaly.description(), REPORTED_BY, MaintenanceOrigin.AUTOMATIC));
                log.warn("Anomalia {} na bicicleta {}: {}; ordem de manutenção pedida",
                        anomaly.type(), bikeId, anomaly.description());
            }
        }
    }
}
