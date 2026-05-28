package pl.zzpj.ai_service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationServiceTest {

    private ClassificationService service;

    @BeforeEach
    void setUp() throws Exception {
        Path modelPath = Paths.get(getClass().getResource("/model/mobilenetv2.onnx").toURI());
        service = new ClassificationService();
        ReflectionTestUtils.setField(service, "modelPath", modelPath.toString());
        service.loadModel();
    }

    @AfterEach
    void tearDown() {
        service.close();
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
