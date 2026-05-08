package pl.zzpj.ai_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "ai.model.path=/tmp/model/mobilenetv2.onnx"
})
class AiServiceApplicationTests {

    @MockitoBean
    ClassificationService classificationService;

    @Test
    void contextLoads() {
    }
}
