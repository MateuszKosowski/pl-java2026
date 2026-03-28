package pl.zzpj.watermark_service.security;

import feign.FeignException; // Zależność z OpenFeign
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Użyjemy loggera do śledzenia błędów
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

    private final AuthClient authClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                if (authClient.validateToken(token)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            "User", null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return; 
                }
            } catch (FeignException e) {
                log.error(AUTH_SERVER_COMMUNICATION_ERROR, e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return; 
            } catch (Exception e) {
                log.error(UNEXPECTED_AUTHENTICATION_ERROR, e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return; 
            }
        }
        
        filterChain.doFilter(request, response);
    }
}