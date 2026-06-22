package pl.zzpj.ai_service.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules for the ai-service module (issue #19 criterion #4), enforced
 * with ArchUnit over the {@code pl.zzpj.ai_service} package.
 *
 * <p>The module has a flat package layout (no {@code controller}/{@code service}
 * sub-packages), so the layering rules are expressed in terms of the Spring
 * {@code @RestController} / {@code @Service} stereotypes rather than package names,
 * which keeps them meaningful instead of vacuous.
 */
@AnalyzeClasses(
        packages = "pl.zzpj.ai_service",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ArchitectureTest {

    /** REST controllers must be named consistently. */
    @ArchTest
    static final ArchRule restControllers_should_be_named_Controller =
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .should().haveSimpleNameEndingWith("Controller")
                    .allowEmptyShould(true);

    /**
     * Controllers are the entry point of the request flow; service-layer classes must
     * not depend on them (dependencies point inward, controller → service, never back).
     */
    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses()
                    .that().areAnnotatedWith(Service.class)
                    .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
                    .allowEmptyShould(true);

    /** Enforce Slf4j logging — no direct System.out / System.err usage. */
    @ArchTest
    static final ArchRule no_classes_should_use_standard_streams =
            noClasses()
                    .should().accessField(System.class, "out")
                    .orShould().accessField(System.class, "err")
                    .as("no class should write to System.out or System.err (use Slf4j instead)")
                    .allowEmptyShould(true);

    /**
     * Plain JUnit assertion that there is at least one controller in the module, so the
     * stereotype-based rules above are exercised against real classes rather than
     * silently passing on an empty set.
     */
    @ArchTest
    static final ArchRule module_should_have_a_rest_controller =
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("pl.zzpj.ai_service..")
                    .allowEmptyShould(false);
}
