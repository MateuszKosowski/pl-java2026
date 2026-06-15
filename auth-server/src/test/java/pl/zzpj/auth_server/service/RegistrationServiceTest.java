package pl.zzpj.auth_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pl.zzpj.auth_server.dto.RegisterRequest;
import pl.zzpj.auth_server.dto.RegisterResponse;
import pl.zzpj.auth_server.entity.User;
import pl.zzpj.auth_server.entity.UserRole;
import pl.zzpj.auth_server.exception.DuplicateUserFieldException;
import pl.zzpj.auth_server.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService registrationService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");

        User savedUser = User.builder()
                .id(1L)
                .username(request.getUsername())
                .email(request.getEmail())
                .role(UserRole.USER)
                .build();

        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = registrationService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("testuser", response.getUsername());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailExists() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(DuplicateUserFieldException.class, () -> registrationService.register(request));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldThrowExceptionWhenUsernameExists() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        assertThrows(DuplicateUserFieldException.class, () -> registrationService.register(request));
        verify(userRepository, never()).saveAndFlush(any());
    }
}
