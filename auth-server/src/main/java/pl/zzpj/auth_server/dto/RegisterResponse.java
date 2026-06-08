package pl.zzpj.auth_server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import pl.zzpj.auth_server.entity.UserRole;

@Data
@AllArgsConstructor
public class RegisterResponse {
    private Long id;
    private String username;
    private String email;
    private UserRole role;
}
