package pl.zzpj.config_server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldReturnUnauthorizedWithoutCredentials() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/ai-service/default", String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void shouldReturnConfigWithCredentials() {
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("admin", "admin")
            .getForEntity("/ai-service/default", String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().contains("ai-service"));
  }
}
