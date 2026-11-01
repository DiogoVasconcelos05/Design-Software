package pt.lisboapedala.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * FF-24: calcula Ca, Ce e I a partir do bytecode (substitui a tabela feita à mão em 02-componentes.md §4),
 * escreve target/architecture/instability.md e verifica o Princípio das Dependências Estáveis.
 */
class InstabilityReportTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(Modules.ROOT);

    private static String moduleOf(JavaClass c) {
        String pn = c.getPackageName();
        if (!pn.startsWith(Modules.ROOT + ".")) return null;
        return pn.substring(Modules.ROOT.length() + 1).split("\\.")[0];
    }

    @Test
    void relatorioDeInstabilidadeEPrincipioDasDependenciasEstaveis() throws IOException {
        Map<String, Set<String>> uses = new TreeMap<>();   // módulo -> módulos que usa
        for (JavaClass c : CLASSES) {
            String from = moduleOf(c);
            if (from == null) continue;
            for (Dependency d : c.getDirectDependenciesFromSelf()) {
                String to = moduleOf(d.getTargetClass());
                if (to != null && !to.equals(from)) uses.computeIfAbsent(from, k -> new TreeSet<>()).add(to);
            }
        }
        List<String> modules = new ArrayList<>(Modules.DOMAIN);
        modules.add(Modules.COMMON);
        modules.add(Modules.API);

        Map<String, Double> instability = new TreeMap<>();
        StringBuilder md = new StringBuilder("| Módulo | Ca | Ce | I |\n|---|---:|---:|---:|\n");
        for (String m : modules) {
            int ce = uses.getOrDefault(m, Set.of()).size();
            int ca = (int) modules.stream().filter(o -> uses.getOrDefault(o, Set.of()).contains(m)).count();
            double i = (ca + ce) == 0 ? 0.0 : (double) ce / (ca + ce);
            instability.put(m, i);
            md.append(String.format("| `%s` | %d | %d | %.2f |%n", m, ca, ce, i));
        }

        // Princípio das Dependências Estáveis: depender de módulos mais estáveis do que nós.
        List<String> sdpViolations = new ArrayList<>();
        uses.forEach((from, tos) -> tos.forEach(to -> {
            String edge = from + "->" + to;
            if (instability.containsKey(to) && instability.get(to) > instability.get(from) + 1e-9
                    && !Modules.SDP_EXCEPTIONS.contains(edge)) {
                sdpViolations.add(String.format("%s (I=%.2f -> I=%.2f)", edge, instability.get(from), instability.get(to)));
            }
        }));

        // Permissões da vista de módulos que nunca são usadas: candidatas a remover (informativo).
        List<String> deadPermissions = new ArrayList<>();
        Modules.ALLOWED.forEach((m, allowed) -> allowed.forEach(a -> {
            if (!uses.getOrDefault(m, Set.of()).contains(a)) deadPermissions.add(m + "->" + a);
        }));

        md.append("\n**Violações do SDP:** ").append(sdpViolations.isEmpty() ? "nenhuma" : sdpViolations).append("\n");
        md.append("\n**Permissões allowed-to-use nunca usadas (candidatas a remover):** ")
                .append(deadPermissions.isEmpty() ? "nenhuma" : deadPermissions).append("\n");
        Path out = Path.of("target", "architecture", "instability.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString());

        assertEquals(0, uses.getOrDefault(Modules.COMMON, Set.of()).size(), "common não pode depender de ninguém (Ce=0)");
        assertTrue(sdpViolations.isEmpty(),
                "Dependências para módulos mais instáveis (corrigir ou aceitar num ADR + Modules.SDP_EXCEPTIONS): "
                        + sdpViolations + "\nRelatório completo em " + out);
    }
}
