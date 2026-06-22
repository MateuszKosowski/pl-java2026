package pl.zzpj.subscription_service.application;

import java.security.Principal;
import org.springframework.stereotype.Component;

@Component
public class UserIdentityResolver {

  public String resolve(Principal principal) {
    if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
      return principal.getName();
    }
    throw new IllegalArgumentException("Authenticated principal is required");
  }
}
