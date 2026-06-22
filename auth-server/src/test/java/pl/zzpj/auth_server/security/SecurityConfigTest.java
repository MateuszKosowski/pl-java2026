package pl.zzpj.auth_server.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.zzpj.auth_server.controller.AuthController;
import pl.zzpj.auth_server.repository.UserRepository;
import pl.zzpj.auth_server.service.JwtService;
import pl.zzpj.auth_server.service.RegistrationService;

/**
 * Verifies the real {@link SecurityConfig} filter chain (filters ENABLED).
 *
 * <p>auth-server is the token issuer for the platform. It exposes no
 * role-protected business endpoints, so there is no role-based 403 code path
 * driven by authorities to exercise here -- only the unauthenticated boundary
 * enforced by Spring Security before request mapping, plus the permitAll
 * whitelist.
 *
 * <p>Note: SecurityConfig declares no authentication entry point (no httpBasic /
 * form login), so an unauthenticated request to a protected route is rejected
 * with 403 rather than 401. The assertion below accepts either rejection code:
 * the load-bearing fact is that a non-whitelisted route is blocked by security
 * (not reachable), while a whitelisted route is not.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void nonWhitelistedRequestWithoutCredentialsIsRejected() throws Exception {
        // /auth/secured-probe is not a permitAll route and is rejected by
        // Spring Security before reaching any handler mapping. With no auth
        // entry point configured, the rejection code is 401 or 403.
        mockMvc
            .perform(get("/auth/secured-probe"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                Assertions.assertTrue(
                    status == 401 || status == 403,
                    "Non-whitelisted route must be rejected by security " +
                    "(401/403), but was " + status
                );
            });
    }

    @Test
    void whitelistedRouteIsReachableWithoutCredentials() throws Exception {
        // /auth/validate is permitAll: it may return 400 (missing param) but
        // must NOT be blocked by security (no 401/403).
        mockMvc
            .perform(get("/auth/validate"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                Assertions.assertTrue(
                    status != 401 && status != 403,
                    "Whitelisted route must not be blocked by security, " +
                    "but was " + status
                );
            });
    }
}
