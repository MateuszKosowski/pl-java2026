package pl.zzpj.subscription_service.controller.dto;

import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.token.TokenOperation;

import java.util.Set;

public record PlanView(
        PlanCode code,
        int monthlyTokens,
        Set<TokenOperation> allowedOperations
) {

    public PlanView {
        allowedOperations = allowedOperations == null ? Set.of() : Set.copyOf(allowedOperations);
    }
}
