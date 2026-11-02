package pt.lisboapedala.api.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.fleet_health.port.TelemetryIngestion;
import pt.lisboapedala.fleet_health.port.TelemetryReading;

/** Endpoint 10 de ARCHITECTURE.md §7.3 (US-2, §7.4.3): telemetria das elétricas, a cada 30 s por bicicleta. */
@RestController
public class TelemetryController {

    private final TelemetryIngestion telemetryIngestion;

    public TelemetryController(TelemetryIngestion telemetryIngestion) {
        this.telemetryIngestion = telemetryIngestion;
    }

    /** 10. {@code POST /telemetry} → 202. */
    @PostMapping("/telemetry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ingest(@RequestBody TelemetryReading reading) {
        telemetryIngestion.ingest(reading);
    }
}
