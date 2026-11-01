package pt.lisboapedala.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Catálogo de fitness functions (ver docs/04-fitness-functions.md para a origem de cada FF-xx).
 * Parametrizado pelo pacote-raiz para que RulesCatchViolationsTest as possa correr contra "canários".
 */
public final class ArchitectureRules {

    private static final List<String> TX = List.of(
            "org.springframework.transaction.annotation.Transactional",
            "jakarta.transaction.Transactional");
    private static final List<String> BEANS = List.of(
            "org.springframework.stereotype.Component", "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository", "org.springframework.web.bind.annotation.RestController",
            "org.springframework.context.annotation.Configuration");
    private static final String[] DB_PACKAGES = {
            "org.springframework.jdbc..", "org.springframework.transaction..", "java.sql..", "javax.sql..", "org.sqlite.."};
    private static final String[] HTTP_CLIENTS = {
            "java.net.http..", "org.springframework.web.client..", "org.springframework.web.reactive..",
            "org.apache.http..", "org.apache.hc..", "okhttp3.."};
    private static final List<String> NET_PREFIXES = List.of(
            "java.net", "org.springframework.web.client", "org.springframework.web.reactive");
    private static final Pattern PERSONAL_DATA = Pattern.compile(
            "(?i).*(user|rider|customer|subscriber|route|path|polyline|gps|track|coordinate|trajectory).*");

    private final String root;

    public ArchitectureRules(String root) { this.root = root; }

    // ---------------------------------------------------------------- helpers de pacotes
    private String pkg(String module) { return root + "." + module + ".."; }
    private String pkg(String module, String sub) { return root + "." + module + "." + sub + ".."; }
    private String[] pkgs(Collection<String> modules) { return modules.stream().map(this::pkg).toArray(String[]::new); }

    public List<ArchRule> all() {
        List<ArchRule> r = new ArrayList<>();
        r.addAll(allowedToUse());                     // FF-01, FF-02
        r.add(noCycles());                            // FF-03
        r.addAll(encapsulation());                    // FF-04
        r.addAll(contractsDoNotDependOnImplementation()); // FF-05
        r.add(queryContract());                       // FF-06
        r.addAll(readOnlyConsumersUseQueryOnly());    // FF-07
        r.addAll(noDatabaseAccess());                 // FF-08
        r.addAll(commonPurity());                     // FF-09
        r.add(tariffPurity());                        // FF-10
        r.add(tariffStateless());                     // FF-10
        r.add(knownPackagesOnly());                   // FF-11
        r.addAll(noJpaNoFieldInjection());            // FF-12
        r.add(httpClientsOnlyInPayment());            // FF-14
        r.add(noNetworkInsideTransaction());          // FF-14
        r.add(noPaymentInsideTransaction());          // FF-15
        r.add(wallClock());                           // FF-16
        r.add(noMutableStatics());                    // FF-17
        r.add(statelessBeans());                      // FF-17
        r.add(queryRecordsHaveNoPersonalData());      // FF-18
        r.add(noStandardStreams());                   // FF-19
        return r;
    }

    // ================================================================ FF-01 / FF-02
    public ArchRule allowedToUse(String module) {
        Set<String> forbidden = new LinkedHashSet<>(Modules.DOMAIN);
        forbidden.remove(module);
        forbidden.removeAll(Modules.ALLOWED.get(module));
        forbidden.add(Modules.API); // ARCHITECTURE.md §2.1: nenhum módulo de domínio depende de api
        return noClasses().that().resideInAPackage(pkg(module))
                .should().dependOnClassesThat().resideInAnyPackage(pkgs(forbidden))
                .as("FF-01 " + module + " só pode usar " + Modules.ALLOWED.get(module) + " e common")
                .because("docs/03 – regras allowed-to-use");
    }

    public List<ArchRule> allowedToUse() {
        List<ArchRule> rules = new ArrayList<>();
        for (String m : Modules.DOMAIN) rules.add(allowedToUse(m));
        Set<String> everyoneElse = new LinkedHashSet<>(Modules.DOMAIN);
        everyoneElse.add(Modules.API);
        rules.add(noClasses().that().resideInAPackage(pkg(Modules.COMMON))
                .should().dependOnClassesThat().resideInAnyPackage(pkgs(everyoneElse))
                .as("FF-02 common não depende de nenhum outro módulo")
                .because("ARCHITECTURE.md §2.3"));
        return rules;
    }

    // ================================================================ FF-03
    public ArchRule noCycles() {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles()
                .as("FF-03 sem ciclos entre módulos")
                .because("ARCHITECTURE.md §5; docs/03 'Não há import loops'");
    }

    // ================================================================ FF-04
    public List<ArchRule> encapsulation() {
        List<ArchRule> rules = new ArrayList<>();
        for (String m : Modules.DOMAIN) {
            rules.add(classes().that().resideInAnyPackage(pkg(m, "internal"), pkg(m, "persistence"))
                    .should().onlyBeAccessed().byAnyPackage(pkg(m))
                    .allowEmptyShould(true)
                    .as("FF-04a " + m + ".internal e .persistence são privados ao módulo")
                    .because("ARCHITECTURE.md §2.2; ADR-005 §1"));
            rules.add(classes().that().resideInAPackage(pkg(m, "persistence"))
                    .should().onlyBeAccessed().byAnyPackage(pkg(m, "internal"), pkg(m, "persistence"))
                    .allowEmptyShould(true)
                    .as("FF-04b " + m + ".persistence só é usada por " + m + ".internal")
                    .because("ADR-005 §1"));
        }
        return rules;
    }

    // ================================================================ FF-05
    public List<ArchRule> contractsDoNotDependOnImplementation() {
        List<ArchRule> rules = new ArrayList<>();
        for (String m : Modules.DOMAIN) {
            rules.add(noClasses().that().resideInAnyPackage(pkg(m, "port"), pkg(m, "query"))
                    .should().dependOnClassesThat().resideInAnyPackage(pkg(m, "internal"), pkg(m, "persistence"))
                    .allowEmptyShould(true)
                    .as("FF-05 " + m + ".port/.query não dependem da implementação")
                    .because("inversão de dependências: o contrato é publicado, a implementação é privada"));
        }
        return rules;
    }

    // ================================================================ FF-06
    public ArchRule queryContract() {
        return classes().that().resideInAPackage(root + "..query..")
                .should(classCondition("ser interface ou record, e com métodos que devolvem valor", (c, ev) -> {
                    if (c.getModifiers().contains(JavaModifier.SYNTHETIC)) return;
                    if (!(c.isInterface() || c.isAssignableTo(Record.class))) {
                        violation(ev, c, c.getName() + " está em ..query.. mas não é interface nem record");
                    }
                    if (c.isInterface()) {
                        for (JavaMethod m : c.getMethods()) {
                            if (!m.getModifiers().contains(JavaModifier.STATIC)
                                    && m.getRawReturnType().getName().equals("void")) {
                                violation(ev, m, m.getFullName() + " devolve void: uma consulta não pode ser um comando");
                            }
                        }
                    }
                }))
                .allowEmptyShould(true)
                .as("FF-06 ..query.. só tem interfaces de leitura e records")
                .because("ADR-005 §3–4 e secção Verificação");
    }

    // ================================================================ FF-07
    public List<ArchRule> readOnlyConsumersUseQueryOnly() {
        List<ArchRule> rules = new ArrayList<>();
        for (String m : List.of(Modules.REPORTING, Modules.REDISTRIBUTION)) {
            Set<String> others = new LinkedHashSet<>(Modules.DOMAIN);
            others.remove(m);
            DescribedPredicate<JavaClass> otherModuleOutsideQuery =
                    JavaClass.Predicates.resideInAnyPackage(pkgs(others))
                            .and(DescribedPredicate.not(JavaClass.Predicates.resideInAPackage("..query..")));
            rules.add(noClasses().that().resideInAPackage(pkg(m))
                    .should().dependOnClassesThat(otherModuleOutsideQuery)
                    .as("FF-07 " + m + " só acede a outros módulos através de ..query..")
                    .because("ARCHITECTURE.md §2.5; ADR-005 §3"));
        }
        return rules;
    }

    // ================================================================ FF-08
    public List<ArchRule> noDatabaseAccess() {
        List<ArchRule> rules = new ArrayList<>();
        for (String m : List.of(Modules.REPORTING, Modules.REDISTRIBUTION, Modules.TARIFF, Modules.API, Modules.COMMON)) {
            rules.add(noClasses().that().resideInAPackage(pkg(m))
                    .should().dependOnClassesThat().resideInAnyPackage(DB_PACKAGES)
                    .as("FF-08 " + m + " não acede à base de dados nem a transações")
                    .because("só leitura via ..query.. (§2.5), função pura (§2.4), api sem lógica (§7.3)"));
        }
        return rules;
    }

    // ================================================================ FF-09
    public List<ArchRule> commonPurity() {
        return List.of(
                classes().that().resideInAPackage(pkg(Modules.COMMON))
                        .should(classCondition("ser record, enum, interface ou exceção", (c, ev) -> {
                            boolean synthetic = c.getModifiers().contains(JavaModifier.SYNTHETIC) || c.isAnonymousClass();
                            boolean ok = c.isAssignableTo(Record.class) || c.isEnum() || c.isInterface()
                                    || c.isAssignableTo(Throwable.class);
                            if (!synthetic && !ok) {
                                violation(ev, c, c.getName() + " em common não é record/enum/interface/exceção");
                            }
                        }))
                        .as("FF-09a common só tem tipos imutáveis: records, enums, interfaces e exceções")
                        .because("ARCHITECTURE.md §5; ADR-001"),
                noClasses().that().resideInAPackage(pkg(Modules.COMMON))
                        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                        .as("FF-09b common não depende de Spring")
                        .because("common é só tipos partilhados, sem lógica nem infraestrutura"));
    }

    // ================================================================ FF-10
    public ArchRule tariffPurity() {
        return classes().that().resideInAPackage(pkg(Modules.TARIFF))
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        pkg(Modules.TARIFF), pkg(Modules.COMMON),
                        "java.lang..", "java.util..", "java.time..", "java.math..",
                        "org.springframework.stereotype..")
                .as("FF-10a tariff_pricing só depende de common e do JDK puro (sem I/O, rede, BD)")
                .because("ARCHITECTURE.md §2.4 e §3; ADR-003");
    }

    public ArchRule tariffStateless() {
        return fields().that().areDeclaredInClassesThat().resideInAPackage(pkg(Modules.TARIFF))
                .should().beFinal()
                .as("FF-10b tariff_pricing não tem estado mutável")
                .because("função pura e sem estado (ADR-003)");
    }

    // ================================================================ FF-11
    public ArchRule knownPackagesOnly() {
        return classes().that().resideInAPackage(root + "..")
                .should(classCondition("estar num módulo e subpacote previstos em ARCHITECTURE.md §1 e §7.2", (c, ev) -> {
                    String pn = c.getPackageName();
                    if (pn.equals(root)) {
                        if (!c.getSimpleName().startsWith("LisboaPedalaApplication")) {
                            violation(ev, c, c.getName() + " está na raiz; só LisboaPedalaApplication é permitida");
                        }
                        return;
                    }
                    String[] parts = pn.substring(root.length() + 1).split("\\.");
                    String module = parts[0];
                    boolean ok;
                    if (module.equals(Modules.COMMON)) ok = true;
                    else if (module.equals(Modules.API)) ok = parts.length >= 2 && Modules.API_SUBPACKAGES.contains(parts[1]);
                    else if (Modules.DOMAIN.contains(module)) ok = parts.length >= 2 && Modules.DOMAIN_SUBPACKAGES.contains(parts[1]);
                    else ok = false;
                    if (!ok) violation(ev, c, c.getName() + " está num pacote não previsto na especificação");
                }))
                .as("FF-11 só existem os módulos e subpacotes especificados")
                .because("ARCHITECTURE.md §0.2: 'Não cries pacotes... que não estejam descritos aqui'");
    }

    // ================================================================ FF-12
    public List<ArchRule> noJpaNoFieldInjection() {
        return List.of(
                noClasses().that().resideInAPackage(root + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                "jakarta.persistence..", "javax.persistence..", "org.hibernate..", "org.springframework.data..")
                        .as("FF-12a sem JPA/Hibernate/Spring Data")
                        .because("ARCHITECTURE.md §7.1: JdbcClient + SQL à mão, sem JPA"),
                noFields().that().areDeclaredInClassesThat().resideInAPackage(root + "..")
                        .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                        .allowEmptyShould(true)
                        .as("FF-12b injeção só por construtor (convenção proposta)")
                        .because("testabilidade e dependências explícitas (Supportability)"));
    }

    // ================================================================ FF-14
    public ArchRule httpClientsOnlyInPayment() {
        return noClasses().that().resideInAPackage(root + "..")
                .and().resideOutsideOfPackage(pkg(Modules.PAYMENT))
                .should().dependOnClassesThat().resideInAnyPackage(HTTP_CLIENTS)
                .as("FF-14a só payment_invoicing fala com sistemas externos (PSP, AT)")
                .because("Availability: o caminho de eventos das docas não pode depender de chamadas remotas");
    }

    public ArchRule noNetworkInsideTransaction() {
        return classes().that().resideInAPackage(root + "..")
                .should(transactionalMethodsMustNotAccess(NET_PREFIXES, "I/O de rede dentro de transação"))
                .as("FF-14b nenhum método @Transactional faz I/O de rede")
                .because("SQLite tem um único escritor: uma chamada lenta dentro da transação bloqueia todas as escritas");
    }

    // ================================================================ FF-15
    public ArchRule noPaymentInsideTransaction() {
        return classes().that().resideInAnyPackage(pkg(Modules.TRIP), pkg(Modules.DOCK))
                .should(transactionalMethodsMustNotAccess(List.of(root + "." + Modules.PAYMENT),
                        "cobrança dentro da transação de fecho"))
                .as("FF-15 a cobrança só é invocada depois do commit do fecho da viagem")
                .because("ARCHITECTURE.md §4; ADR-004 §4");
    }

    // ================================================================ FF-16
    public ArchRule wallClock() {
        return noClasses().that().resideInAPackage(root + "..")
                .should().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .as("FF-16 o tempo vem de occurredAt ou de um Clock injetado, nunca do relógio do servidor")
                .because("ADR-004 §5: um reenvio tardio não pode alterar os blocos de 15 min cobrados");
    }

    // ================================================================ FF-17
    public ArchRule noMutableStatics() {
        return fields().that().areDeclaredInClassesThat().resideInAPackage(root + "..")
                .and().areStatic()
                .should().beFinal()
                .as("FF-17a sem estado estático mutável")
                .because("ADR-001: escalar por réplicas atrás de um load balancer");
    }

    public ArchRule statelessBeans() {
        return classes().that().resideInAPackage(root + "..")
                .should(classCondition("ter campos de instância final se for um bean Spring", (c, ev) -> {
                    if (BEANS.stream().noneMatch(c::isAnnotatedWith)) return;
                    for (JavaField f : c.getFields()) {
                        if (!f.getModifiers().contains(JavaModifier.STATIC) && !f.getModifiers().contains(JavaModifier.FINAL)) {
                            violation(ev, f, f.getFullName() + " é estado mutável num bean singleton");
                        }
                    }
                }))
                .as("FF-17b beans Spring sem estado mutável de instância")
                .because("ARCHITECTURE.md §3 (elasticidade): réplicas não partilham memória");
    }

    // ================================================================ FF-18
    public ArchRule queryRecordsHaveNoPersonalData() {
        return classes().that().resideInAPackage(root + "..query..")
                .should(classCondition("não ter campos de utilizador nem de percurso", (c, ev) -> {
                    for (JavaField f : c.getFields()) {
                        if (!f.getModifiers().contains(JavaModifier.STATIC) && PERSONAL_DATA.matcher(f.getName()).matches()) {
                            violation(ev, f, f.getFullName() + " parece identificar o utilizador ou o percurso (RGPD)");
                        }
                    }
                }))
                .allowEmptyShould(true)
                .as("FF-18 as consultas partilhadas entre módulos não expõem dados pessoais")
                .because("ADR-005: o percurso da viagem é dado pessoal; o relatório só precisa de agregados");
    }

    // ================================================================ FF-19
    public ArchRule noStandardStreams() {
        return noClasses().that().resideInAPackage(root + "..")
                .and().resideOutsideOfPackage(pkg(Modules.API, "cli"))
                .should(GeneralCodingRules.ACCESS_STANDARD_STREAMS)
                .as("FF-19 logging por SLF4J; System.out/err só na CLI")
                .because("ARCHITECTURE.md §7.1 (Logging) e Supportability/observabilidade (correlationId em MDC)");
    }

    // ================================================================ condições reutilizáveis
    private static ArchCondition<JavaClass> classCondition(String description, BiConsumer<JavaClass, ConditionEvents> body) {
        return new ArchCondition<JavaClass>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) { body.accept(item, events); }
        };
    }

    private static void violation(ConditionEvents events, Object origin, String message) {
        events.add(SimpleConditionEvent.violated(origin, message));
    }

    /** Métodos transacionais (no método ou na classe) que acedem diretamente a pacotes proibidos. */
    private static ArchCondition<JavaClass> transactionalMethodsMustNotAccess(List<String> forbiddenPrefixes, String why) {
        return classCondition("não ter métodos @Transactional que acedam a " + forbiddenPrefixes + " (" + why + ")", (c, ev) -> {
            for (JavaMethod m : c.getMethods()) {
                boolean tx = TX.stream().anyMatch(a -> m.isAnnotatedWith(a) || c.isAnnotatedWith(a));
                if (!tx) continue;
                for (JavaAccess<?> access : m.getAccessesFromSelf()) {
                    String target = access.getTargetOwner().getPackageName();
                    for (String prefix : forbiddenPrefixes) {
                        if (target.equals(prefix) || target.startsWith(prefix + ".")) {
                            violation(ev, m, m.getFullName() + " é transacional e acede a "
                                    + access.getTargetOwner().getName() + " (" + why + ")");
                        }
                    }
                }
            }
        });
    }
}
