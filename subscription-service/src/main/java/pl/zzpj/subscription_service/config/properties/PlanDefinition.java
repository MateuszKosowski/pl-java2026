package pl.zzpj.subscription_service.config.properties;

import pl.zzpj.subscription_service.domain.token.TokenOperation;

import java.util.Set;

public record PlanDefinition(int monthlyTokens, Set<TokenOperation> allowedOperations) {

    public PlanDefinition {
        allowedOperations = allowedOperations == null ? Set.of() : Set.copyOf(allowedOperations);
    }
}
