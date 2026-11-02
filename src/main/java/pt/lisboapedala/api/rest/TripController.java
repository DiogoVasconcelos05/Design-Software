package pt.lisboapedala.api.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.trip_management.port.TripService;
import pt.lisboapedala.trip_management.port.TripView;

/** Endpoints 4, 5 e 6 de ARCHITECTURE.md §7.3 (US-1, Req. 8a): desbloqueio e consulta de viagens. */
@RestController
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /**
     * 4. {@code POST /trips/unlock} → 202 com a viagem {@code REQUESTED}: só passa a {@code IN_PROGRESS} quando
     * chegar o {@code BIKE_RELEASED} da doca (§7.4.1); o cliente acompanha pelo endpoint 5.
     */
    @PostMapping("/trips/unlock")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TripView requestUnlock(@Valid @RequestBody UnlockRequest body) {
        return tripService.requestUnlock(body.credential(), body.dockId());
    }

    /** 5. {@code GET /trips/{tripId}} → 200, ou 404. */
    @GetMapping("/trips/{tripId}")
    public TripView getTrip(@PathVariable UUID tripId) {
        return tripService.getTrip(tripId);
    }

    /** 6. {@code GET /users/{userId}/trips?limit=20&offset=0} → 200, mais recente primeiro. */
    @GetMapping("/users/{userId}/trips")
    public List<TripView> history(@PathVariable String userId,
                                  @RequestParam(defaultValue = "20") int limit,
                                  @RequestParam(defaultValue = "0") int offset) {
        return tripService.history(userId, limit, offset);
    }

    /** Corpo do endpoint 4: {@code {credential:{kind,value}, dockId}}. */
    public record UnlockRequest(@NotNull Credential credential, @NotBlank String dockId) {
    }
}
