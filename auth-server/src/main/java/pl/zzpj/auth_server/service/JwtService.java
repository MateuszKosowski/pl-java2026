package pl.zzpj.auth_server.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.zzpj.auth_server.entity.UserRole;

@Service
public class JwtService {
  @Value("${app.jwt.secret}")
  private String secretKey;

  public String generateToken(String username, Long userId, UserRole role) {
    return Jwts.builder()
        .subject(username)
        .claim("userId", userId)
        .claim("role", role.name())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24h
        .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
        .compact();
  }

  public void validateToken(String token) {
    Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
        .build()
        .parseSignedClaims(token);
  }
}
