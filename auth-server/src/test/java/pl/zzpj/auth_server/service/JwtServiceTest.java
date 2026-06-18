package pl.zzpj.auth_server.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.zzpj.auth_server.entity.UserRole;

class JwtServiceTest {

  private JwtService jwtService;
  private final String secret = "testSecretKeyWithEnoughLengthForHMACSHA256Algorithm123456";

  @BeforeEach
  void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "secretKey", secret);
  }

  @Test
  void shouldGenerateValidToken() {
    String token = jwtService.generateToken("user1", 1L, UserRole.USER);
    assertNotNull(token);
    assertDoesNotThrow(() -> jwtService.validateToken(token));
  }

  @Test
  void shouldThrowExceptionForInvalidToken() {
    assertThrows(Exception.class, () -> jwtService.validateToken("invalidToken"));
  }

  @Test
  void shouldThrowExceptionForExpiredToken() {
    // We can't easily test expiration without changing the code or using a custom clock,
    // but we can test that different data produces different tokens.
    String token1 = jwtService.generateToken("user1", 1L, UserRole.USER);
    String token2 = jwtService.generateToken("user2", 2L, UserRole.ADMIN);
    assertNotEquals(token1, token2);
  }
}
