package pl.zzpj.subscription_service.domain.subscription;

import pl.zzpj.subscription_service.domain.token.TokenOperation;

import java.util.Objects;
import java.util.Set;

public record SubscriptionPlan(
        PlanCode code,
        int monthlyTokens,
        Set<TokenOperation> allowedOperations
) {

    public SubscriptionPlan {
        Objects.requireNonNull(code, "code must not be null");
        if (monthlyTokens < 0) {
            throw new IllegalArgumentException("monthlyTokens must not be negative");
        }
        allowedOperations = allowedOperations == null ? Set.of() : Set.copyOf(allowedOperations);
    }

    public boolean allows(TokenOperation operation) {
        return allowedOperations.contains(operation);
    }
}
