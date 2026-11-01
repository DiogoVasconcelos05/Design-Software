package pt.lisboapedala.architecture;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fonte única de verdade das fitness functions. Espelha ARCHITECTURE.md §1–§2 e docs/03.
 * Se a especificação mudar, muda-se AQUI e no documento, no mesmo commit.
 */
public final class Modules {

    private Modules() {}

    public static final String ROOT = "pt.lisboapedala";
    public static final String COMMON = "common";
    public static final String API = "api";

    public static final String TRIP = "trip_management";
    public static final String DOCK = "dock_gateway";
    public static final String PAYMENT = "payment_invoicing";
    public static final String TARIFF = "tariff_pricing";
    public static final String REPORTING = "municipal_reporting";
    public static final String REDISTRIBUTION = "redistribution_planning";

    public static final List<String> DOMAIN = List.of(
            "identity_access", TRIP, DOCK, "station_inventory", TARIFF,
            PAYMENT, "fleet_health", "maintenance_orders", REDISTRIBUTION, REPORTING);

    /** docs/03 "Regras allowed-to-use" (todos podem ainda usar common; api pode usar tudo). */
    public static final Map<String, Set<String>> ALLOWED = Map.of(
            DOCK, Set.of(TRIP, "station_inventory", "fleet_health"),
            TRIP, Set.of("identity_access", "station_inventory", PAYMENT),
            PAYMENT, Set.of(TARIFF),
            "fleet_health", Set.of("maintenance_orders"),
            REDISTRIBUTION, Set.of("station_inventory"),
            REPORTING, Set.of(TRIP, "station_inventory"),
            "identity_access", Set.of(),
            "station_inventory", Set.of(),
            TARIFF, Set.of(),
            "maintenance_orders", Set.of());

    /** ARCHITECTURE.md §7.2 */
    public static final Set<String> DOMAIN_SUBPACKAGES = Set.of("query", "port", "internal", "persistence");
    public static final Set<String> API_SUBPACKAGES = Set.of("rest", "cli", "config");

    /** ADR-001 (consequência negativa): common tem de se manter pequeno. Ajustar com justificação. */
    public static final int COMMON_MAX_TOP_LEVEL_TYPES = 40;

    /** Tabelas de faturas (retenção de 10 anos): nome contém um destes termos. */
    public static final Pattern INVOICE_TABLE = Pattern.compile("invoice|fatura");

    /**
     * Exceções aceites ao Princípio das Dependências Estáveis, no formato "origem->destino".
     * Cada entrada tem de ter um ADR que a justifique.
     */
    public static final Set<String> SDP_EXCEPTIONS = Set.of();
}
