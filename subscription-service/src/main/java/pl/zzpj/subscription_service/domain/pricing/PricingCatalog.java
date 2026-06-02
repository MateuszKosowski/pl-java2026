package pl.zzpj.subscription_service.domain.pricing;

import pl.zzpj.subscription_service.domain.token.TokenOperation;

import java.util.Map;
import java.util.Objects;

public record PricingCatalog(Map<TokenOperation, Integer> tokenCosts) {

    public PricingCatalog {
        tokenCosts = tokenCosts == null ? Map.of() : Map.copyOf(tokenCosts);
        tokenCosts.forEach((operation, tokens) -> {
            Objects.requireNonNull(operation, "operation must not be null");
            if (tokens == null || tokens < 0) {
                throw new IllegalArgumentException("token cost must not be negative");
            }
        });
    }

    public int costOf(TokenOperation operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        Integer tokens = tokenCosts.get(operation);
        if (tokens == null) {
            throw new IllegalArgumentException("No token cost configured for operation " + operation);
        }
        return tokens;
    }
}
