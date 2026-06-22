package pl.zzpj.config_server.architecture;

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
 * ArchUnit architecture rules scoped to the config-server base package.
 * config-server is an infrastructure module with no controller/service/repository
 * layers, so most rules match nothing; allowEmptyShould(true) keeps them green.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "pl.zzpj.config_server";

    private static JavaClasses classesUnderTest;

    @BeforeAll
    static void importClasses() {
        classesUnderTest = new ClassFileImporter()
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

        rule.check(classesUnderTest);
    }

    @Test
    void controllerLayerShouldNotBeAccessedByLowerLayers() {
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage(
                "..service..",
                "..repository..",
                "..entity.."
            )
            .should()
            .accessClassesThat()
            .resideInAPackage("..controller..")
            .allowEmptyShould(true);

        rule.check(classesUnderTest);
    }

    @Test
    void noClassShouldUseStandardStreams() {
        ArchRule rule = noClasses()
            .should()
            .accessField(System.class, "out")
            .orShould()
            .accessField(System.class, "err")
            .because("logging must go through Slf4j, not System.out/System.err")
            .allowEmptyShould(true);

        rule.check(classesUnderTest);
    }
}
