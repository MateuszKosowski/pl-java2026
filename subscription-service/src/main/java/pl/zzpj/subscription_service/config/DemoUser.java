package pl.zzpj.subscription_service.config;

import pl.zzpj.subscription_service.domain.subscription.PlanCode;

public record DemoUser(
        String userId,
        PlanCode planCode,
        int availableTokens,
        int reservedTokens
) {
}
