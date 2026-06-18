package pl.zzpj.config_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "CONFIG_SERVER_USER=test-user",
      "CONFIG_SERVER_PASSWORD=test-password"
    })
class ApplicationTests {

  @Test
  void contextLoads() {}
}
