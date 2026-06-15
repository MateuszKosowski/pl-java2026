package pl.zzpj.ai_service.bdd;

import io.cucumber.java.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import pl.zzpj.ai_service.support.OnnxRuntimeAvailability;

/**
 * Cucumber {@code @BeforeAll} guard that runs <em>before</em> the Spring context is
 * created for the BDD scenarios.
 *
 * <p>The real {@link pl.zzpj.ai_service.ClassificationService} loads the ONNX model in a
 * {@code @PostConstruct}, so if the native ONNX Runtime cannot initialize on the host the
 * whole Spring context would fail to start. This guard turns that environment-level
 * problem into a clean <em>skip</em> (via a JUnit assumption) instead of a hard failure,
 * while still running and asserting the real classification flow wherever ONNX works.
 */
public class OnnxAvailabilityHooks {

    @BeforeAll
    public static void requireOnnxRuntime() {
        Assumptions.assumeTrue(
                OnnxRuntimeAvailability.isAvailable(),
                "ONNX Runtime native library could not initialize on this host/JDK; "
                        + "skipping ONNX-backed BDD scenarios.");
    }
}
