package pl.zzpj.ai_service.security;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.zzpj.ai_service.ClassificationController;
import pl.zzpj.ai_service.ClassificationResult;
import pl.zzpj.ai_service.ClassificationService;
import pl.zzpj.ai_service.client.AuthClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link ClassificationController} (issue #19 criterion #5).
 *
 * <p>Loads the real {@link SecurityConfig} and {@link JwtFilter} on top of a sliced
 * MVC context. The Feign {@link AuthClient} and the {@link ClassificationService}
 * are mocked.
 *
 * <p><b>Deviations from the issue text</b> (these tests assert the REAL behaviour of
 * the current {@link SecurityConfig}/{@link JwtFilter}, not the issue's wording):
 * <ul>
 *   <li>The issue mentions a <b>403</b> for expired/tampered tokens. This service has
 *       <em>no role model</em>; auth is binary. An invalid/expired/tampered token makes
 *       {@link JwtFilter} write <b>401</b> ("Invalid or expired token") — there is no
 *       role-based 403 path, so we assert 401, not a fabricated 403.</li>
 *   <li>For a request with <b>no Authorization header</b>, {@link JwtFilter} simply
 *       passes through and Spring Security's {@code .anyRequest().authenticated()} is
 *       enforced by the <em>default</em> {@code Http403ForbiddenEntryPoint} (no custom
 *       {@code AuthenticationEntryPoint} is configured), which yields <b>403</b>, not
 *       401. We assert the real 403 here.</li>
 * </ul>
 * Summary of the genuine status codes: no header → 403, invalid token → 401,
 * valid token → 200.
 */
@WebMvcTest(ClassificationController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class ClassificationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private ClassificationService classificationService;

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "test-dog.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void noAuthorizationHeader_isRejected() throws Exception {
        // No Authorization header → JwtFilter passes through → Spring Security's
        // .anyRequest().authenticated() is enforced by the default Http403ForbiddenEntryPoint → 403.
        // (See class Javadoc: real behaviour is 403 here, not the 401 the issue text implies.)
        mockMvc.perform(multipart("/api/classify")
                        .file(imageFile())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedOrExpiredToken_returns401() throws Exception {
        // validateToken == false → JwtFilter itself writes 401 ("Invalid or expired token").
        Mockito.when(authClient.validateToken(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/classify")
                        .file(imageFile())
                        .header("Authorization", "Bearer tampered.or.expired")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validToken_returns200() throws Exception {
        // validateToken == true → authentication set → request reaches the controller.
        Mockito.when(authClient.validateToken(any())).thenReturn(true);
        Mockito.when(classificationService.classify(any()))
                .thenReturn(new ClassificationResult("Samoyed", "dog", 0.9, 0.95, List.of()));

        mockMvc.perform(multipart("/api/classify")
                        .file(imageFile())
                        .header("Authorization", "Bearer valid.jwt.token")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk());
    }
}
