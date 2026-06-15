package pl.zzpj.auth_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.zzpj.auth_server.dto.RegisterRequest;
import pl.zzpj.auth_server.dto.RegisterResponse;
import pl.zzpj.auth_server.entity.UserRole;
import pl.zzpj.auth_server.repository.UserRepository;
import pl.zzpj.auth_server.service.JwtService;
import pl.zzpj.auth_server.service.RegistrationService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

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
    void shouldRegisterUser() throws Exception {
        RegisterResponse response = new RegisterResponse(
            1L,
            "user",
            "email@test.com",
            UserRole.USER
        );
        when(
            registrationService.register(any(RegisterRequest.class))
        ).thenReturn(response);

        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"user\",\"email\":\"email@test.com\",\"password\":\"password\"}"
                    )
            )
            .andExpect(status().isCreated());
    }

    @Test
    void shouldFailValidationOnRegister() throws Exception {
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"\",\"email\":\"invalid-email\",\"password\":\"\"}"
                    )
            )
            .andExpect(status().isBadRequest());
    }
}
