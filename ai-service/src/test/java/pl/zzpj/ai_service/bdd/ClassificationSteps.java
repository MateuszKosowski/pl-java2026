package pl.zzpj.ai_service.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import pl.zzpj.ai_service.ClassificationResult;
import pl.zzpj.ai_service.ClassificationService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for the ONNX image-classification feature.
 *
 * <p>Uses the real {@link ClassificationService} bean (model loaded from the test
 * resource) and AssertJ for all assertions (issue #19 criterion #1).
 */
public class ClassificationSteps {

    @Autowired
    private ClassificationService classificationService;

    private ClassificationResult result;
    private List<String> synsetLabels;

    @Given("the MobileNetV2 classification model is loaded")
    public void theModelIsLoaded() {
        assertThat(classificationService).as("ClassificationService bean").isNotNull();
        this.synsetLabels = readSynsetLabels();
        assertThat(synsetLabels).as("synset.txt labels").isNotEmpty();
    }

    @When("I classify the image {string}")
    public void iClassifyTheImage(String imageName) throws Exception {
        byte[] bytes;
        try (InputStream is = getClass().getResourceAsStream("/model/" + imageName)) {
            assertThat(is).as("test image /model/%s on classpath", imageName).isNotNull();
            bytes = is.readAllBytes();
        }
        MockMultipartFile file = new MockMultipartFile("file", imageName, "image/jpeg", bytes);
        this.result = classificationService.classify(file);
        assertThat(result).as("classification result").isNotNull();
    }

    @Then("the returned label is a non-blank entry present in synset.txt")
    public void theLabelIsAValidSynsetEntry() {
        assertThat(result.label()).as("returned label").isNotBlank();
        assertThat(synsetLabels)
                .as("returned label '%s' must be a known ImageNet synset entry", result.label())
                .contains(result.label());
    }

    @Then("the returned category is {string}")
    public void theReturnedCategoryIs(String expectedCategory) {
        assertThat(result.category())
                .as("broad category for label '%s'", result.label())
                .isEqualTo(expectedCategory);
    }

    @Then("the confidence is greater than {int}")
    public void theConfidenceIsGreaterThan(int threshold) {
        assertThat(result.confidence())
                .as("top-1 confidence")
                .isGreaterThan((double) threshold);
    }

    private List<String> readSynsetLabels() {
        try (InputStream is = getClass().getResourceAsStream("/model/synset.txt")) {
            assertThat(is).as("synset.txt on classpath").isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException("Could not read synset.txt", e);
        }
    }
}
