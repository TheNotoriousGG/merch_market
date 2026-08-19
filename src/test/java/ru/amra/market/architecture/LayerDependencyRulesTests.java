package ru.amra.market.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "ru.amra.market")
class LayerDependencyRulesTests {

    @ArchTest
    static final ArchRule MODULES_ARE_FREE_OF_CYCLES = slices().matching("ru.amra.market.(*)..")
            .should()
            .beFreeOfCycles()
            .because("module dependencies must form a directed acyclic graph")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_INDEPENDENT = noClasses()
            .that()
            .resideInAnyPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "..api..",
                    "..infrastructure..",
                    "..generated..")
            .because("domain code must remain independent from frameworks and adapters")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses()
            .that()
            .resideInAnyPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..infrastructure..", "..generated..")
            .because("application use cases may only point inward or to declared ports")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule API_DOES_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that()
            .resideInAnyPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..")
            .because("transport adapters must not depend on persistence implementations")
            .allowEmptyShould(true);
}
