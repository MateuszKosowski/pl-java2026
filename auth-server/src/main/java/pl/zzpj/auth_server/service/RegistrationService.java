package pl.zzpj.auth_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zzpj.auth_server.dto.RegisterRequest;
import pl.zzpj.auth_server.dto.RegisterResponse;
import pl.zzpj.auth_server.entity.User;
import pl.zzpj.auth_server.entity.UserRole;
import pl.zzpj.auth_server.exception.DuplicateUserFieldException;
import pl.zzpj.auth_server.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class RegistrationService {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateUserFieldException("email", "Email is already registered.");
    }
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new DuplicateUserFieldException("username", "Username is already taken.");
    }

    User user =
        User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(UserRole.USER)
            .build();

    try {
      User savedUser = userRepository.saveAndFlush(user);
      return new RegisterResponse(
          savedUser.getId(), savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole());
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateUserFieldException("user", "Username or email is already registered.");
    }
  }
}
