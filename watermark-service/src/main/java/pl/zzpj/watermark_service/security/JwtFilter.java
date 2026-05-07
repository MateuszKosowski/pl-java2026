package pl.zzpj.watermark_service.security;

import feign.FeignException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.zzpj.watermark_service.client.AuthClient;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String AUTH_SERVER_COMMUNICATION_ERROR = "Error communicating with auth-server during token validation: {}";
    private static final String UNEXPECTED_AUTHENTICATION_ERROR = "Unexpected authentication error: {}";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_USER_PRINCIPAL = "User";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CHARACTER_ENCODING_UTF8 = "UTF-8";
    private static final String ERROR_JSON_FORMAT = "{\"error\": \"%s\"}";

    private static final String INVALID_TOKEN_MSG = "Invalid or expired token";
    private static final String AUTH_SERVICE_UNAVAILABLE_MSG = "Authentication service is currently unavailable";
    private static final String UNEXPECTED_ERROR_MSG = "An unexpected error occurred during authentication";

    private final AuthClient authClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                if (authClient.validateToken(token)) {
                    String principal = extractPrincipalFromToken(token);
                    log.info("JWT validated successfully. Extracted principal: {}", principal);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    handleError(response, INVALID_TOKEN_MSG);
                    return;
                }
            } catch (FeignException e) {
                log.error(AUTH_SERVER_COMMUNICATION_ERROR, e.getMessage());
                handleError(response, AUTH_SERVICE_UNAVAILABLE_MSG);
                return;
            } catch (Exception e) {
                log.error(UNEXPECTED_AUTHENTICATION_ERROR, e.getMessage());
                handleError(response, UNEXPECTED_ERROR_MSG);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractPrincipalFromToken(String token) {
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length < 2) return DEFAULT_USER_PRINCIPAL;
            String payload = new String(java.util.Base64.getUrlDecoder().decode(chunks[1]), java.nio.charset.StandardCharsets.UTF_8);

            String subject = extractJsonStringField(payload, "sub");
            String userId = extractJsonNumberField(payload, "userId");

            if (subject != null && userId != null) {
                return subject + "-" + userId;
            }
        } catch (Exception e) {
            log.warn("Could not extract custom principal from token, falling back to default", e);
        }
        return DEFAULT_USER_PRINCIPAL;
    }

    private String extractJsonStringField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end != -1 ? json.substring(start, end) : null;
    }

    private String extractJsonNumberField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start == -1) return null;
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

        String json = String.format(ERROR_JSON_FORMAT, message);

        response.getWriter().write(json);
    }
}
