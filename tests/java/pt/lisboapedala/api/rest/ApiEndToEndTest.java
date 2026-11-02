package pt.lisboapedala.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A aplicação completa (todos os módulos, Spring e SQLite num ficheiro temporário) atrás da api: confirma que
 * cada porta chamada pela api tem implementação e percorre a US-1 só por REST (§7.4.1 e §7.4.2), incluindo o
 * reenvio do evento de encaixe (ADR-004: uma viagem fechada, uma fatura). O contexto é fechado no fim da classe
 * para libertar o ficheiro SQLite antes de o diretório temporário ser apagado.
 */
@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
class ApiEndToEndTest {

    private static final String BASE = "/api/v1";

    @TempDir
    static Path dir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dir.resolve("api-e2e.db"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    /** Não há endpoint para criar estações (o inventário físico não entra pela api); semeia-se pela BD. */
    @BeforeEach
    void seedStation() {
        Timestamp longAgo = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
        jdbc.sql("INSERT OR IGNORE INTO stations (station_id, name, parish, latitude, longitude) "
                + "VALUES ('S-1', 'Cais do Sodré', 'Misericórdia', 38.706, -9.144)").update();
        jdbc.sql("INSERT OR IGNORE INTO docks (dock_id, station_id, bike_id, status, updated_at) "
                + "VALUES ('S-1-D1', 'S-1', 'B-1', 'OCCUPIED', :t)").param("t", longAgo).update();
        jdbc.sql("INSERT OR IGNORE INTO docks (dock_id, station_id, bike_id, status, updated_at) "
                + "VALUES ('S-1-D2', 'S-1', NULL, 'FREE', :t)").param("t", longAgo).update();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private ResultActions dockEvent(UUID eventId, String dockId, String type, String occurredAt, UUID commandId)
            throws Exception {
        String command = commandId == null ? "null" : "\"" + commandId + "\"";
        return send(post(BASE + "/dock-events"), """
                {"eventId":"%s","dockId":"%s","bikeId":"B-1","eventType":"%s","occurredAt":"%s","commandId":%s}"""
                .formatted(eventId, dockId, type, occurredAt, command));
    }

    @Test
    void desbloquearDevolverECobrarSoPelaApi() throws Exception {
        // Req. 1: registo e meio de pagamento (US-1)
        String email = "rita+" + UUID.randomUUID() + "@example.pt";
        String userId = JsonPath.read(body(send(post(BASE + "/users"), """
                {"name":"Rita","email":"%s","passType":"OCCASIONAL"}""".formatted(email))
                .andExpect(status().isCreated())), "$.userId");
        mvc.perform(get(BASE + "/users/" + userId)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        send(put(BASE + "/users/" + userId + "/payment-method"), """
                {"kind":"MB_WAY","token":"psp-tok-rita"}""").andExpect(status().isNoContent());

        // §7.4.1: desbloqueio → 202 REQUESTED; BIKE_RELEASED da doca → IN_PROGRESS
        String tripId = JsonPath.read(body(send(post(BASE + "/trips/unlock"), """
                {"credential":{"kind":"QR_CODE","value":"%s"},"dockId":"S-1-D1"}""".formatted(userId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"))), "$.tripId");
        dockEvent(UUID.randomUUID(), "S-1-D1", "BIKE_RELEASED", "2026-11-01T17:40:00Z", UUID.fromString(tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));
        mvc.perform(get(BASE + "/trips/" + tripId)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // §7.4.2: BIKE_DOCKED 20 min depois fecha a viagem; o reenvio do mesmo evento também dá 200 (ADR-004)
        UUID docked = UUID.randomUUID();
        dockEvent(docked, "S-1-D2", "BIKE_DOCKED", "2026-11-01T18:00:00Z", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));
        dockEvent(docked, "S-1-D2", "BIKE_DOCKED", "2026-11-01T18:00:00Z", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        mvc.perform(get(BASE + "/trips/" + tripId))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.durationMinutes").value(20));
        mvc.perform(get(BASE + "/users/" + userId + "/trips"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(BASE + "/users/" + userId + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tripId").value(tripId));

        // Req. 4: a ocupação reflete a bicicleta encaixada na outra doca
        mvc.perform(get(BASE + "/stations/S-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBikes").value(1))
                .andExpect(jsonPath("$.freeDocks").value(1));
    }

    @Test
    void credencialDesconhecidaDa403SemAbrirADoca() throws Exception {
        send(post(BASE + "/trips/unlock"), """
                {"credential":{"kind":"QR_CODE","value":"usr_inexistente"},"dockId":"S-1-D1"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASS_INVALID"));
    }

    @Test
    void avariaPorTelemetriaAbreOrdemQueOTecnicoConclui() throws Exception {
        String bike = "B-" + UUID.randomUUID();
        send(post(BASE + "/telemetry"), """
                {"bikeId":"%s","occurredAt":"2026-11-01T17:40:00Z","batteryTemperatureCelsius":20.0,"brakeFault":true}"""
                .formatted(bike)).andExpect(status().isAccepted());

        String orders = body(mvc.perform(get(BASE + "/maintenance-orders").param("status", "OPEN"))
                .andExpect(status().isOk()));
        String orderId = JsonPath.<List<String>>read(orders,
                "$[?(@.bikeId == '" + bike + "')].orderId").get(0);
        assertThat(JsonPath.<List<String>>read(orders, "$[?(@.bikeId == '" + bike + "')].origin"))
                .containsExactly("AUTOMATIC");

        // §7.4.3: o reporte manual da mesma avaria não cria outra ordem
        send(post(BASE + "/maintenance-orders"), """
                {"bikeId":"%s","faultType":"BRAKES","description":"não trava","reportedBy":"usr_1"}""".formatted(bike))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId));

        send(post(BASE + "/maintenance-orders/" + orderId + "/assign"), """
                {"technicianId":"tec-1"}""").andExpect(status().isOk());
        mvc.perform(post(BASE + "/maintenance-orders/" + orderId + "/start")).andExpect(status().isOk());
        send(post(BASE + "/maintenance-orders/" + orderId + "/complete"), """
                {"notes":"cabo do travão substituído"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post(BASE + "/maintenance-orders/" + orderId + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void consultasDeLeituraEActuator() throws Exception {
        mvc.perform(get(BASE + "/redistribution/routes").param("vans", "2").param("vanCapacity", "20"))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/reports/monthly").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
