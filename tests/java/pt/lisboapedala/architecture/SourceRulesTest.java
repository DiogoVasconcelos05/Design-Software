package pt.lisboapedala.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** FF-20 a FF-23: regras que o bytecode não mostra (imports, SQL, schema). Correm de src/main. */
class SourceRulesTest {

    private static final Path JAVA = Path.of("src", "main", "java");
    private static final Path DB = Path.of("src", "main", "resources", "db");

    private static void assertNone(String rule, List<SourceScanner.Finding> findings) {
        assertTrue(findings.isEmpty(), rule + ":\n" + findings.stream()
                .map(SourceScanner.Finding::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void FF20_semImportsCuringa() {
        assertNone("FF-20 ARCHITECTURE.md §5 proíbe imports curinga", SourceScanner.wildcardImports(JAVA));
    }

    @Test
    void FF21_semSqlNemChavesEstrangeirasEntreModulos() {
        assertNone("FF-21 persistência privada por módulo (ARCHITECTURE.md §5, ADR-005 §1)",
                SourceScanner.foreignTableAccess(JAVA, DB));
    }

    @Test
    void FF22a_dockGatewayGaranteUnicidadeDoEventId() {
        assertTrue(SourceScanner.hasUniqueOn(DB.resolve("dock_gateway/schema.sql"), "event_?id"),
                "FF-22a ADR-004 §2: db/dock_gateway/schema.sql tem de ter UNIQUE/PRIMARY KEY sobre o eventId");
    }

    @Test
    void FF22b_tripManagementGuardaOEventIdDoFechoComUnicidade() {
        assertTrue(SourceScanner.hasUniqueOn(DB.resolve("trip_management/schema.sql"), "event_?id"),
                "FF-22b ADR-004 §3: db/trip_management/schema.sql tem de ter UNIQUE sobre o eventId do fecho");
    }

    @Test
    void FF23_faturasNuncaSaoApagadas() {
        Set<String> invoiceTables = SourceScanner.tablesMatching(DB, Modules.INVOICE_TABLE);
        assertFalse(invoiceTables.isEmpty(),
                "Nenhuma tabela de faturas em db/*/schema.sql (nome com 'invoice' ou 'fatura'); a regra seria vazia");
        assertNone("FF-23 faturas guardadas 10 anos (enunciado): nada as pode apagar",
                SourceScanner.destructiveStatementsOn(JAVA, invoiceTables));
    }
}
