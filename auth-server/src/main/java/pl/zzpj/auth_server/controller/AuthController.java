package pl.zzpj.auth_server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.zzpj.auth_server.dto.LoginRequest;
import pl.zzpj.auth_server.dto.LoginResponse;
import pl.zzpj.auth_server.dto.RegisterRequest;
import pl.zzpj.auth_server.dto.RegisterResponse;
import pl.zzpj.auth_server.repository.UserRepository;
import pl.zzpj.auth_server.service.JwtService;
import pl.zzpj.auth_server.service.RegistrationService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and registration API")
public class AuthController {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RegistrationService registrationService;

  @PostMapping("/register")
  @Operation(
      summary = "Register user",
      description = "Creates a new user account with the provided details.")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = registrationService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  @Operation(summary = "Login user", description = "Authenticates a user and returns a JWT token.")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
    return userRepository
        .findByEmail(request.getEmail())
        .map(
            user -> {
              if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                String token =
                    jwtService.generateToken(user.getUsername(), user.getId(), user.getRole());
                return ResponseEntity.ok(new LoginResponse(token));
              }
              return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
            })
        .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found"));
  }

  @PostMapping("/validate")
  @Operation(summary = "Validate token", description = "Validates the provided JWT token.")
  public ResponseEntity<Boolean> validateToken(@RequestParam String token) {
    try {
      jwtService.validateToken(token);
      return ResponseEntity.ok(true);
    } catch (Exception e) {
      return ResponseEntity.ok(false);
    }
  }
}
