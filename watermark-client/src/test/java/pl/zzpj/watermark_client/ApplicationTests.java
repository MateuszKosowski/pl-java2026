package pl.zzpj.watermark_client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false"
})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
