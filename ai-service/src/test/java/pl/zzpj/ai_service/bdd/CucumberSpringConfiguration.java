package pl.zzpj.ai_service.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.zzpj.ai_service.client.AuthClient;

/**
 * Wires the Cucumber scenarios into a real Spring Boot context (issue #19 criterion #1).
 *
 * <p>Eureka discovery and Spring Cloud Config are disabled exactly as in
 * {@code AiServiceApplicationTests}. The ONNX model path points at the real test
 * resource so the BDD steps exercise the genuine classification logic. The Feign
 * {@code AuthClient} is mocked because it is unrelated to the classification flow.
 */
@CucumberContextConfiguration
@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "ai.model.path=${user.dir}/build/resources/test/model/mobilenetv2.onnx"
})
public class CucumberSpringConfiguration {

    @MockitoBean
    AuthClient authClient;
}
