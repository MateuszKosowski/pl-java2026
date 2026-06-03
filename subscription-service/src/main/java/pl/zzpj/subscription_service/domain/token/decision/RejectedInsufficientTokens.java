package pl.zzpj.subscription_service.domain.token.decision;

import pl.zzpj.subscription_service.domain.token.TokenOperation;

public record RejectedInsufficientTokens(
        TokenOperation operation,
        int requiredTokens,
        int availableTokens
) implements TokenDecision {
}
