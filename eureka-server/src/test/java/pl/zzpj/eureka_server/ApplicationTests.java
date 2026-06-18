package pl.zzpj.eureka_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.cloud.config.enabled=false"})
class ApplicationTests {

  @Test
  void contextLoads() {}
}
