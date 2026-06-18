package pl.zzpj.subscription_service.security;

import feign.FeignException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.zzpj.subscription_service.client.AuthClient;

@Component
public class JwtFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CHARACTER_ENCODING_UTF8 = "UTF-8";

  private final AuthClient authClient;

  public JwtFilter(AuthClient authClient) {
    this.authClient = authClient;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX.length());
    try {
      if (Boolean.TRUE.equals(authClient.validateToken(token))) {
        String principal = extractPrincipalFromToken(token);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
        return;
      }
      handleError(response, "Invalid or expired token");
    } catch (FeignException exception) {
      handleError(response, "Authentication service is currently unavailable");
    } catch (IllegalArgumentException exception) {
      handleError(response, "Could not read token principal");
    }
  }

  private String extractPrincipalFromToken(String token) {
    String[] chunks = token.split("\\.");
    if (chunks.length < 2) {
      throw new IllegalArgumentException("JWT payload is missing");
    }
    String payload = new String(Base64.getUrlDecoder().decode(chunks[1]), StandardCharsets.UTF_8);
    String userId = extractJsonNumberField(payload, "userId");
    if (userId == null) {
      throw new IllegalArgumentException("JWT principal claims are missing");
    }
    return userId;
  }

  private String extractJsonNumberField(String json, String field) {
    String search = "\"" + field + "\":";
    int start = json.indexOf(search);
    if (start == -1) {
      return null;
    }
    start += search.length();
    int end = start;
    while (end < json.length() && Character.isDigit(json.charAt(end))) {
      end++;
    }
    return end > start ? json.substring(start, end) : null;
  }

  private void handleError(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(CONTENT_TYPE_JSON);
    response.setCharacterEncoding(CHARACTER_ENCODING_UTF8);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
