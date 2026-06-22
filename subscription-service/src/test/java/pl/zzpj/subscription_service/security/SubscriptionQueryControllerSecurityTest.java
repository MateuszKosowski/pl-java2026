package pl.zzpj.subscription_service.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.zzpj.subscription_service.application.SubscriptionQueryService;
import pl.zzpj.subscription_service.application.UserIdentityResolver;
import pl.zzpj.subscription_service.client.AuthClient;
import pl.zzpj.subscription_service.controller.SubscriptionQueryController;

/**
 * Security slice test for a SECURED (non-whitelisted) endpoint: GET /api/subscriptions/plans.
 *
 * <p>Wires the real {@link SecurityConfig} + {@link JwtFilter} so the actual auth behavior is
 * exercised. {@link AuthClient} and the controller's service collaborators are mocked.
 *
 * <p>DEVIATION FROM ISSUE #19: the issue mentions a 403 for expired/tampered tokens and (loosely)
 * a 401 for unauthenticated access. This service has NO role model. The REAL behavior is:
 * <ul>
 *   <li>NO Authorization header -> Spring Security has no custom authentication entry point on this
 *       STATELESS chain, so the default kicks in and returns <b>403</b> (not 401).</li>
 *   <li>Header present but {@link AuthClient#validateToken} is false (tampered/expired) ->
 *       {@link JwtFilter} writes <b>401</b> "Invalid or expired token" (never 403).</li>
 *   <li>Valid token -> filter authenticates with EMPTY authorities and the request proceeds (200).</li>
 * </ul>
 * We assert the REAL behavior, not a fabricated 403-for-tampered.
 */
@WebMvcTest(SubscriptionQueryController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class SubscriptionQueryControllerSecurityTest {

    // A token whose 2nd ('.'-separated) segment is base64url-encoded JSON containing a numeric
    // userId, as required by JwtFilter#extractPrincipalFromToken. Header/signature are irrelevant
    // because validateToken is mocked.
    private static final String VALID_TOKEN = buildToken("{\"userId\":42}");
    private static final String TAMPERED_TOKEN = "tampered.jwt.token";

    private static String buildToken(String payloadJson) {
        String header = base64Url("{\"alg\":\"HS256\"}");
        String payload = base64Url(payloadJson);
        return header + "." + payload + ".signature";
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private SubscriptionQueryService subscriptionQueryService;

    @MockitoBean
    private UserIdentityResolver userIdentityResolver;

    @Test
    void shouldRejectWhenNoAuthorizationHeader() throws Exception {
        // No header -> JwtFilter passes through (no Bearer), then Spring Security rejects the
        // unauthenticated request. With no custom authentication entry point on this STATELESS
        // chain the default applies and the status is 403 (the issue loosely says 401; the REAL
        // behavior of this SecurityConfig is 403 — documented deviation).
        mockMvc
            .perform(get("/api/subscriptions/plans"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn401WhenTokenIsInvalidOrTampered() throws Exception {
        // Header present but authClient says the token is invalid -> JwtFilter writes 401.
        when(authClient.validateToken(TAMPERED_TOKEN)).thenReturn(false);

        mockMvc
            .perform(
                get("/api/subscriptions/plans")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TAMPERED_TOKEN)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn200WhenTokenIsValid() throws Exception {
        // Valid token -> JwtFilter authenticates (empty authorities) and the request proceeds.
        when(authClient.validateToken(VALID_TOKEN)).thenReturn(true);
        when(subscriptionQueryService.availablePlans()).thenReturn(List.of());

        mockMvc
            .perform(
                get("/api/subscriptions/plans")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
            )
            .andExpect(status().isOk());
    }
}
