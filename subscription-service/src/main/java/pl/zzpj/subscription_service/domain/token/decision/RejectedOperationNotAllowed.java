package pl.zzpj.subscription_service.domain.token.decision;

import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.token.TokenOperation;

public record RejectedOperationNotAllowed(PlanCode planCode, TokenOperation operation) implements TokenDecision {
}
