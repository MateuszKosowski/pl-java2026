package pl.zzpj.config_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"eureka.client.enabled=false"
})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
