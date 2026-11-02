package pt.lisboapedala.fleet_health.persistence;

import java.time.Instant;
import java.util.List;

/**
 * Acesso à tabela {@code fleet_active_anomalies}. Privado a fleet_health.
 */
public interface ActiveAnomalyRepository {

    List<ActiveAnomalyEntity> findByBike(String bikeId);

    /**
     * Regista a anomalia como ativa; a unicidade é garantida pela chave primária {@code (bike_id, anomaly_type)}.
     *
     * @return false se já estava ativa (outra leitura ou réplica registou-a primeiro)
     */
    boolean registerIfNew(ActiveAnomalyEntity anomaly);

    /**
     * Dá a anomalia como resolvida, mas só se foi detetada antes de {@code resolvedAt}, para que uma leitura
     * antiga reenviada fora de ordem não apague uma deteção mais recente.
     *
     * @return true se a anomalia deixou de estar ativa
     */
    boolean clearIfDetectedBefore(String bikeId, String anomalyType, Instant resolvedAt);
}
