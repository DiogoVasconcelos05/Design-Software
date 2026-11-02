package pt.lisboapedala.api.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderView;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;
import pt.lisboapedala.maintenance_orders.port.MaintenanceService;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;

/** Endpoints 13 a 17 de ARCHITECTURE.md §7.3 (US-2, §7.4.3): reporte manual e ciclo de vida das ordens. */
@RestController
@RequestMapping("/maintenance-orders")
public class MaintenanceOrderController {

    private final MaintenanceService maintenanceService;

    public MaintenanceOrderController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /**
     * 13. {@code POST /maintenance-orders} → 201. A ordem entra como {@code USER_REPORT}; se já houver uma ordem
     * ativa para {@code (bikeId, faultType)}, maintenance_orders devolve essa em vez de criar outra.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceOrderView open(@Valid @RequestBody ReportFaultRequest body) {
        return maintenanceService.open(new OpenMaintenanceOrder(
                body.bikeId(), body.faultType(), body.description(), body.reportedBy(), MaintenanceOrigin.USER_REPORT));
    }

    /** 14. {@code GET /maintenance-orders?status=OPEN} → 200; sem {@code status}, as ordens ativas. */
    @GetMapping
    public List<MaintenanceOrderView> list(@RequestParam(required = false) MaintenanceOrderStatus status) {
        return maintenanceService.list(status);
    }

    /** 15. {@code POST /maintenance-orders/{id}/assign} → 200. */
    @PostMapping("/{id}/assign")
    public MaintenanceOrderView assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest body) {
        return maintenanceService.assign(id, body.technicianId());
    }

    /** 16. {@code POST /maintenance-orders/{id}/start} → 200. */
    @PostMapping("/{id}/start")
    public MaintenanceOrderView start(@PathVariable UUID id) {
        return maintenanceService.start(id);
    }

    /** 17. {@code POST /maintenance-orders/{id}/complete} → 200. As notas são opcionais. */
    @PostMapping("/{id}/complete")
    public MaintenanceOrderView complete(@PathVariable UUID id, @RequestBody(required = false) CompleteRequest body) {
        return maintenanceService.complete(id, body == null ? null : body.notes());
    }

    /** Corpo do endpoint 13: {@code {bikeId, faultType, description, reportedBy}}. */
    public record ReportFaultRequest(String bikeId, @NotNull FaultType faultType, String description, String reportedBy) {
    }

    /** Corpo do endpoint 15: {@code {technicianId}}. */
    public record AssignRequest(String technicianId) {
    }

    /** Corpo do endpoint 17: {@code {notes}}. */
    public record CompleteRequest(String notes) {
    }
}
