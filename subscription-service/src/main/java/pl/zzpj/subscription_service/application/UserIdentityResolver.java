package pl.zzpj.subscription_service.application;

import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class UserIdentityResolver {

    public String resolve(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        throw new IllegalArgumentException("Authenticated principal is required");
    }
}
