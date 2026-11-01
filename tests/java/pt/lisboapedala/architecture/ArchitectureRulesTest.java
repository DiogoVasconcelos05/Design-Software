package pt.lisboapedala.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Corre todas as FF-xx de ArchitectureRules sobre o código de produção.
 * Enquanto não existir código, falha: é intencional (uma regra que não analisa nada não prova nada).
 */
class ArchitectureRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(Modules.ROOT);

    @TestFactory
    Stream<DynamicTest> regrasDeArquitetura() {
        return new ArchitectureRules(Modules.ROOT).all().stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(CLASSES)));
    }

    /** O esqueleto tem todos os componentes lógicos, mesmo que vazios (enunciado 3.3). */
    @Test
    void todosOsModulosTemPeloMenosUmaClasse() {
        List<String> modules = new ArrayList<>(Modules.DOMAIN);
        modules.add(Modules.COMMON);
        modules.add(Modules.API);
        List<String> missing = modules.stream()
                .filter(m -> CLASSES.stream().noneMatch(c -> c.getPackageName().startsWith(Modules.ROOT + "." + m)))
                .toList();
        assertTrue(missing.isEmpty(), "Módulos sem nenhuma classe: " + missing);
    }

    /** ADR-001: common não pode crescer sem controlo. */
    @Test
    void commonNaoCrescePorDescuido() {
        long topLevelTypes = CLASSES.stream()
                .filter(c -> c.getPackageName().startsWith(Modules.ROOT + "." + Modules.COMMON))
                .filter(c -> c.getEnclosingClass().isEmpty())
                .count();
        assertTrue(topLevelTypes <= Modules.COMMON_MAX_TOP_LEVEL_TYPES,
                "common tem " + topLevelTypes + " tipos (máx. " + Modules.COMMON_MAX_TOP_LEVEL_TYPES
                        + "). Justificar o aumento num ADR ou mover tipos para o módulo dono.");
    }

    /** ARCHITECTURE.md §7.3: um único @RestControllerAdvice, em api.rest. */
    @Test
    void umUnicoTratadorDeErros() {
        List<JavaClass> advices = CLASSES.stream()
                .filter(c -> c.isAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice"))
                .toList();
        assertEquals(1, advices.size(), "Esperado 1 @RestControllerAdvice, encontrados: " + advices);
        assertTrue(advices.get(0).getPackageName().startsWith(Modules.ROOT + ".api.rest"),
                "O @RestControllerAdvice tem de viver em api.rest: " + advices.get(0).getName());
    }
}
