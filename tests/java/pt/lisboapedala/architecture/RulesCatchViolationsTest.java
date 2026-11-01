package pt.lisboapedala.architecture;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * FF-25 (meta): uma fitness function que nunca falha não prova nada. Estes testes correm as regras contra
 * classes "canário" (src/test/java/canary/**), que violam de propósito, e exigem que cada regra falhe.
 * Os canários ficam fora das regras de produção (DoNotIncludeTests) e só aqui são importados.
 */
class RulesCatchViolationsTest {

    private static final String CANARY = "canary";
    private static final JavaClasses BAD = new ClassFileImporter()
            .withImportOption(new ImportOption.OnlyIncludeTests())
            .importPackages(CANARY);
    private static final ArchitectureRules RULES = new ArchitectureRules(CANARY);

    private static void mustFail(ArchRule rule) {
        assertThrows(AssertionError.class, () -> rule.check(BAD),
                "A regra devia ter apanhado o canário mas passou: " + rule.getDescription());
    }

    @Test void matrizApanhaDependenciaProibida()   { mustFail(RULES.allowedToUse("trip_management")); }
    @Test void tariffPuraApanhaIO()                { mustFail(RULES.tariffPurity()); }
    @Test void relogioApanhaInstantNow()           { mustFail(RULES.wallClock()); }
    @Test void cobrancaDentroDaTransacao()         { mustFail(RULES.noPaymentInsideTransaction()); }
    @Test void queryComVoidEComDadosPessoais()     { mustFail(RULES.queryContract()); mustFail(RULES.queryRecordsHaveNoPersonalData()); }
    @Test void estadoEstaticoMutavel()             { mustFail(RULES.noMutableStatics()); }
}
