package pl.zzpj.ai_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "ai.model.path=/tmp/model/mobilenetv2.onnx"
})
class AiServiceApplicationTests {

    @MockBean
    ClassificationService classificationService;

    @Test
    void contextLoads() {
    }
}
