package pl.zzpj.ai_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import pl.zzpj.ai_service.support.OnnxRuntimeAvailability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ClassificationServiceTest {

    private ClassificationService service;

    @BeforeEach
    void setUp() throws Exception {
        // The ONNX Runtime native library does not initialize on every host/JDK
        // combination (e.g. onnxruntime 1.21.1 under JDK 21 on this Windows box fails
        // with "DLL initialization routine failed", while it works under JDK 25).
        // Skip rather than hard-fail when the native runtime is unavailable.
        assumeTrue(OnnxRuntimeAvailability.isAvailable(),
                "ONNX Runtime native library could not initialize on this host/JDK");
        Path modelPath = Paths.get(getClass().getResource("/model/mobilenetv2.onnx").toURI());
        service = new ClassificationService();
        ReflectionTestUtils.setField(service, "modelPath", modelPath.toString());
        service.loadModel();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void classifiesSamoyedImageAsDogCategory() throws Exception {
        Path imagePath = Paths.get(getClass().getResource("/model/test-dog.jpg").toURI());
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-dog.jpg", "image/jpeg", Files.readAllBytes(imagePath));

        ClassificationResult result = service.classify(file);

        assertNotNull(result.label());
        assertEquals("dog", result.category(),
                "Samoyed image should map to 'dog' category, got label='" + result.label() + "'");
        assertTrue(result.confidence() > 0.1,
                "Top-1 confidence should be > 0.1, got " + result.confidence());
        assertTrue(result.categoryConfidence() >= result.confidence(),
                "categoryConfidence should be >= top-1 confidence when top-1 is in the category, got "
                        + result.categoryConfidence() + " vs " + result.confidence());
        assertTrue(result.categoryConfidence() <= 1.0,
                "categoryConfidence must not exceed 1.0, got " + result.categoryConfidence());
        assertEquals(3, result.top3().size());
        assertTrue(result.top3().get(0).confidence() >= result.top3().get(1).confidence(),
                "top3 must be sorted by descending confidence");
    }
}
