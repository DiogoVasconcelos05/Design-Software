package pt.lisboapedala.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pt.lisboapedala.common.event.DockEventType;
import pt.lisboapedala.common.exception.BusinessRuleException;
import pt.lisboapedala.common.exception.DomainException;
import pt.lisboapedala.common.exception.ErrorCode;
import pt.lisboapedala.common.valueobject.Credential;
import pt.lisboapedala.common.valueobject.CredentialKind;
import pt.lisboapedala.common.valueobject.PassType;
import pt.lisboapedala.dock_gateway.port.DockEventIngestion;
import pt.lisboapedala.dock_gateway.port.DockEventSubmission;
import pt.lisboapedala.dock_gateway.port.IngestOutcome;
import pt.lisboapedala.fleet_health.port.TelemetryIngestion;
import pt.lisboapedala.fleet_health.port.TelemetryReading;
import pt.lisboapedala.identity_access.port.IdentityService;
import pt.lisboapedala.identity_access.port.RegisterUserCommand;
import pt.lisboapedala.identity_access.port.UserProfileView;
import pt.lisboapedala.identity_access.query.IdentityQuery;
import pt.lisboapedala.maintenance_orders.port.FaultType;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderStatus;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrderView;
import pt.lisboapedala.maintenance_orders.port.MaintenanceOrigin;
import pt.lisboapedala.maintenance_orders.port.MaintenanceService;
import pt.lisboapedala.maintenance_orders.port.OpenMaintenanceOrder;
import pt.lisboapedala.municipal_reporting.query.MonthlyReport;
import pt.lisboapedala.municipal_reporting.query.MonthlyReportQuery;
import pt.lisboapedala.municipal_reporting.query.ParishCo2Record;
import pt.lisboapedala.payment_invoicing.port.PaymentMethodKind;
import pt.lisboapedala.payment_invoicing.port.PaymentService;
import pt.lisboapedala.redistribution_planning.query.RedistributionQuery;
import pt.lisboapedala.station_inventory.query.StationOccupancyQuery;
import pt.lisboapedala.trip_management.port.TripService;
import pt.lisboapedala.trip_management.port.TripStatus;
import pt.lisboapedala.trip_management.port.TripView;

/**
 * Camada web da api (ARCHITECTURE.md §7.3) com as portas dos módulos simuladas: cada endpoint só converte
 * DTO ↔ chamada da porta indicada na tabela, e os erros saem em problem+json com o código e o correlationId.
 */
@WebMvcTest
class ApiRestTest {

    private static final String BASE = "/api/v1";
    private static final Instant T0 = Instant.parse("2026-11-01T17:40:00Z");
    private static final UUID TRIP_ID = UUID.fromString("7f1c2a64-1111-4c3b-9a55-000000000001");

    @Autowired MockMvc mvc;

    @MockBean IdentityService identityService;
    @MockBean IdentityQuery identityQuery;
    @MockBean PaymentService paymentService;
    @MockBean TripService tripService;
    @MockBean DockEventIngestion dockEventIngestion;
    @MockBean TelemetryIngestion telemetryIngestion;
    @MockBean StationOccupancyQuery stationOccupancyQuery;
    @MockBean MaintenanceService maintenanceService;
    @MockBean RedistributionQuery redistributionQuery;
    @MockBean MonthlyReportQuery monthlyReportQuery;

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static TripView requestedTrip() {
        return new TripView(TRIP_ID, "usr_1", TripStatus.REQUESTED, "D-1", null, null, T0, null, null, null);
    }

    // ------------------------------------------------------------------ identity_access (1, 2)

    @Test
    void registaUtilizadorDevolve201ComPerfilELocation() throws Exception {
        when(identityService.register(any())).thenReturn(new UserProfileView(
                "usr_1", "Ana", "ana@example.pt", null, PassType.ANNUAL, true, T0));

        postJson("/users", """
                {"name":"Ana","email":"ana@example.pt","passType":"ANNUAL"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/users/usr_1"))
                .andExpect(jsonPath("$.userId").value("usr_1"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-01T17:40:00Z"));

        verify(identityService).register(new RegisterUserCommand("Ana", "ana@example.pt", null, PassType.ANNUAL));
    }

    @Test
    void registoComEmailInvalidoDa400SemChamarOModulo() throws Exception {
        postJson("/users", """
                {"name":"Ana","email":"sem-arroba","passType":"ANNUAL"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        verifyNoInteractions(identityService);
    }

    @Test
    void perfilInexistenteDa404EmProblemJsonComCorrelationIdRecebido() throws Exception {
        when(identityQuery.findById("usr_x")).thenReturn(Optional.empty());

        mvc.perform(get(BASE + "/users/usr_x").header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    // ------------------------------------------------------------------ payment_invoicing (3, 7)

    @Test
    void registaMeioDePagamentoDevolve204() throws Exception {
        mvc.perform(put(BASE + "/users/usr_1/payment-method").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MB_WAY","token":"psp-tok-1"}"""))
                .andExpect(status().isNoContent());

        verify(paymentService).registerPaymentMethod("usr_1", PaymentMethodKind.MB_WAY, "psp-tok-1");
    }

    @Test
    void meioDePagamentoSemTokenDa400() throws Exception {
        mvc.perform(put(BASE + "/users/usr_1/payment-method").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"CARD"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void faturasUsamPaginacaoPorOmissao() throws Exception {
        when(paymentService.invoicesOf("usr_1", 20, 0)).thenReturn(List.of());

        mvc.perform(get(BASE + "/users/usr_1/invoices")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

        verify(paymentService).invoicesOf("usr_1", 20, 0);
    }

    // ------------------------------------------------------------------ trip_management (4, 5, 6)

    @Test
    void desbloqueioDevolve202ComViagemRequested() throws Exception {
        when(tripService.requestUnlock(any(), any())).thenReturn(requestedTrip());

        postJson("/trips/unlock", """
                {"credential":{"kind":"QR_CODE","value":"usr_1"},"dockId":"D-1"}""")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tripId").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        verify(tripService).requestUnlock(new Credential(CredentialKind.QR_CODE, "usr_1"), "D-1");
    }

    @Test
    void desbloqueioSemCredencialDa400() throws Exception {
        postJson("/trips/unlock", """
                {"dockId":"D-1"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        verifyNoInteractions(tripService);
    }

    @Test
    void historicoPassaLimitEOffset() throws Exception {
        when(tripService.history("usr_1", 5, 10)).thenReturn(List.of(requestedTrip()));

        mvc.perform(get(BASE + "/users/usr_1/trips").param("limit", "5").param("offset", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tripId").value(TRIP_ID.toString()));
    }

    @Test
    void tripIdMalFormadoDa400() throws Exception {
        mvc.perform(get(BASE + "/trips/nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    /** §7.3: mapeamento de cada ErrorCode para o estado HTTP. */
    @ParameterizedTest
    @CsvSource({
            "VALIDATION, 400", "NOT_FOUND, 404", "CONFLICT, 409", "PASS_INVALID, 403",
            "DOCK_UNAVAILABLE, 409", "PAYMENT_FAILED, 502", "TAX_SUBMISSION_FAILED, 502", "INTERNAL, 500"})
    void cadaErrorCodeTemOSeuEstadoHttp(ErrorCode code, int httpStatus) throws Exception {
        when(tripService.getTrip(TRIP_ID)).thenThrow(new DomainException(code, "falhou: " + code));

        mvc.perform(get(BASE + "/trips/" + TRIP_ID))
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(httpStatus))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.detail").value("falhou: " + code));
    }

    @Test
    void erroInesperadoDa500SemExporODetalheInterno() throws Exception {
        when(tripService.getTrip(TRIP_ID)).thenThrow(new IllegalStateException("segredo interno"));

        mvc.perform(get(BASE + "/trips/" + TRIP_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.detail").value("Erro interno inesperado."))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // ------------------------------------------------------------------ dock_gateway (POST /dock-events*)

    @Test
    void eventoDuplicadoDaTambem200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(dockEventIngestion.ingest(any())).thenReturn(IngestOutcome.DUPLICATE);

        postJson("/dock-events", """
                {"eventId":"%s","dockId":"D-1","bikeId":"B-1","eventType":"BIKE_RELEASED",
                 "occurredAt":"2026-11-01T17:40:00Z","commandId":"%s"}""".formatted(eventId, TRIP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        ArgumentCaptor<DockEventSubmission> sent = ArgumentCaptor.forClass(DockEventSubmission.class);
        verify(dockEventIngestion).ingest(sent.capture());
        assertThat(sent.getValue().commandId()).isEqualTo(TRIP_ID);
        assertThat(sent.getValue().event().eventId()).isEqualTo(eventId);
        assertThat(sent.getValue().event().eventType()).isEqualTo(DockEventType.BIKE_RELEASED);
        assertThat(sent.getValue().event().occurredAt()).isEqualTo(T0);
    }

    @Test
    void loteDevolveUmResultadoPorEventoNaOrdemRecebida() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(dockEventIngestion.ingestBatch(anyList())).thenReturn(List.of(IngestOutcome.UNMATCHED, IngestOutcome.PROCESSED));

        postJson("/dock-events/batch", """
                [{"eventId":"%s","dockId":"D-1","bikeId":"B-1","eventType":"BIKE_DOCKED","occurredAt":"2026-11-01T18:00:00Z"},
                 {"eventId":"%s","dockId":"D-2","bikeId":"B-2","eventType":"BIKE_DOCKED","occurredAt":"2026-11-01T17:00:00Z"}]
                """.formatted(first, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(first.toString()))
                .andExpect(jsonPath("$[0].outcome").value("UNMATCHED"))
                .andExpect(jsonPath("$[1].eventId").value(second.toString()))
                .andExpect(jsonPath("$[1].outcome").value("PROCESSED"));
    }

    @Test
    void eventoSemEventIdDa400ParaADocaReenviar() throws Exception {
        postJson("/dock-events", """
                {"dockId":"D-1","bikeId":"B-1","eventType":"BIKE_DOCKED","occurredAt":"2026-11-01T17:40:00Z"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        verifyNoInteractions(dockEventIngestion);
    }

    @Test
    void loteComEventoInvalidoDa400() throws Exception {
        postJson("/dock-events/batch", """
                [{"dockId":"D-1","bikeId":"B-1","eventType":"BIKE_DOCKED","occurredAt":"2026-11-01T17:40:00Z"}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        verifyNoInteractions(dockEventIngestion);
    }

    @Test
    void loteRecusadoPelaGatewayDa400() throws Exception {
        when(dockEventIngestion.ingestBatch(anyList()))
                .thenThrow(new BusinessRuleException(ErrorCode.VALIDATION, "O lote tem de ter pelo menos um evento"));

        postJson("/dock-events/batch", "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    // ------------------------------------------------------------------ fleet_health (10)

    @Test
    void telemetriaDevolve202() throws Exception {
        postJson("/telemetry", """
                {"bikeId":"B-1","occurredAt":"2026-11-01T17:40:00Z","batteryTemperatureCelsius":71.5,"brakeFault":false}""")
                .andExpect(status().isAccepted());

        verify(telemetryIngestion).ingest(new TelemetryReading("B-1", T0, 71.5, false));
    }

    // ------------------------------------------------------------------ station_inventory (11, 12)

    @Test
    void estacoesDevolvemOcupacao() throws Exception {
        when(stationOccupancyQuery.findAll()).thenReturn(List.of());

        mvc.perform(get(BASE + "/stations")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void estacaoInexistenteDa404() throws Exception {
        when(stationOccupancyQuery.findByStationId("S-x")).thenReturn(Optional.empty());

        mvc.perform(get(BASE + "/stations/S-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------------ maintenance_orders (13 a 17)

    private static MaintenanceOrderView order(MaintenanceOrderStatus status) {
        return new MaintenanceOrderView(TRIP_ID, "B-1", FaultType.BRAKES, MaintenanceOrigin.USER_REPORT, status,
                "travão solto", "usr_1", null, T0, null, null, null);
    }

    @Test
    void reporteManualAbreOrdemUserReport() throws Exception {
        when(maintenanceService.open(any())).thenReturn(order(MaintenanceOrderStatus.OPEN));

        postJson("/maintenance-orders", """
                {"bikeId":"B-1","faultType":"BRAKES","description":"travão solto","reportedBy":"usr_1"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("USER_REPORT"));

        verify(maintenanceService).open(new OpenMaintenanceOrder(
                "B-1", FaultType.BRAKES, "travão solto", "usr_1", MaintenanceOrigin.USER_REPORT));
    }

    @Test
    void listaFiltraPorEstadoOuDevolveAsAtivas() throws Exception {
        mvc.perform(get(BASE + "/maintenance-orders").param("status", "OPEN")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/maintenance-orders")).andExpect(status().isOk());

        verify(maintenanceService).list(MaintenanceOrderStatus.OPEN);
        verify(maintenanceService).list(null);
    }

    @Test
    void transicoesDaOrdemDevolvem200() throws Exception {
        when(maintenanceService.assign(TRIP_ID, "tec-7")).thenReturn(order(MaintenanceOrderStatus.ASSIGNED));
        when(maintenanceService.start(TRIP_ID)).thenReturn(order(MaintenanceOrderStatus.IN_PROGRESS));
        when(maintenanceService.complete(eq(TRIP_ID), any())).thenReturn(order(MaintenanceOrderStatus.COMPLETED));

        postJson("/maintenance-orders/" + TRIP_ID + "/assign", """
                {"technicianId":"tec-7"}""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        mvc.perform(post(BASE + "/maintenance-orders/" + TRIP_ID + "/start")).andExpect(status().isOk());
        postJson("/maintenance-orders/" + TRIP_ID + "/complete", """
                {"notes":"pastilhas trocadas"}""").andExpect(status().isOk());

        verify(maintenanceService).complete(TRIP_ID, "pastilhas trocadas");
    }

    // ------------------------------------------------------------------ redistribution_planning (18)

    @Test
    void rotasPassamOsParametros() throws Exception {
        when(redistributionQuery.suggestRoutes(2, 20)).thenReturn(List.of());

        mvc.perform(get(BASE + "/redistribution/routes").param("vans", "2").param("vanCapacity", "20"))
                .andExpect(status().isOk());

        verify(redistributionQuery).suggestRoutes(2, 20);
    }

    @Test
    void rotasComParametrosInvalidosDao400() throws Exception {
        when(redistributionQuery.suggestRoutes(0, 20)).thenThrow(new IllegalArgumentException("vans > 0"));

        mvc.perform(get(BASE + "/redistribution/routes").param("vans", "0").param("vanCapacity", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(get(BASE + "/redistribution/routes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    // ------------------------------------------------------------------ municipal_reporting (19)

    @Test
    void relatorioMensal() throws Exception {
        YearMonth october = YearMonth.of(2026, 10);
        when(monthlyReportQuery.generate(october)).thenReturn(new MonthlyReport(october, 1, new BigDecimal("2.00"),
                new BigDecimal("0.168"), List.of(new ParishCo2Record("Estrela", 1, new BigDecimal("2.00"),
                new BigDecimal("0.168")))));

        mvc.perform(get(BASE + "/reports/monthly").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.parishBreakdown[0].parish").value("Estrela"));
    }

    @Test
    void mesMalFormadoDa400() throws Exception {
        mvc.perform(get(BASE + "/reports/monthly").param("month", "outubro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        verifyNoInteractions(monthlyReportQuery);
    }

    // ------------------------------------------------------------------ transversal

    @Test
    void geraCorrelationIdQuandoNaoVemOuEInvalido() throws Exception {
        String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        when(stationOccupancyQuery.findAll()).thenReturn(List.of());

        mvc.perform(get(BASE + "/stations"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(uuid)));
        mvc.perform(get(BASE + "/stations").header("X-Correlation-Id", "linha\nforjada no log"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(uuid)));
    }

    @Test
    void caminhoForaDaBaseNaoExiste() throws Exception {
        mvc.perform(get("/stations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void jsonInvalidoDa400() throws Exception {
        postJson("/telemetry", "{nao e json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        verifyNoInteractions(telemetryIngestion);
    }
}
