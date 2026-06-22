package pl.zzpj.subscription_service.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * ArchUnit architecture rules scoped to the {@code pl.zzpj.subscription_service} package.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "pl.zzpj.subscription_service";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
    }

    @Test
    void restControllersShouldBeNamedController() {
        ArchRule rule = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void controllerLayerShouldNotBeAccessedByApplicationOrDomain() {
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage(BASE_PACKAGE + ".application..", BASE_PACKAGE + ".domain..")
            .should()
            .accessClassesThat()
            .resideInAPackage(BASE_PACKAGE + ".controller..")
            .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void noClassesShouldUseStandardStreamsForLogging() {
        ArchRule rule = noClasses()
            .should()
            .accessField(System.class, "out")
            .orShould()
            .accessField(System.class, "err")
            .because("use SLF4J for logging instead of System.out / System.err")
            .allowEmptyShould(true);

        rule.check(importedClasses);
    }
}
