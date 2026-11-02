package pt.lisboapedala.fleet_health.port;

/**
 * Porta publicada por fleet_health para a entrada de telemetria das elétricas
 * ({@code POST /telemetry}, endpoint 10 de ARCHITECTURE.md §7.3). Usada pela api.
 *
 * <p>Recebe o pico de carga das bicicletas (uma leitura a cada 30 s), por isso uma leitura sem anomalias novas
 * nem resolvidas não escreve na base de dados.
 */
public interface TelemetryIngestion {

    /**
     * Analisa a leitura e, se revelar uma anomalia nova, abre uma ordem de manutenção {@code AUTOMATIC}
     * (§7.4.3). Devolver sem exceção corresponde ao 202; uma exceção significa que a anomalia não ficou
     * registada e a leitura seguinte da mesma bicicleta volta a tentar.
     */
    void ingest(TelemetryReading reading);
}
