package pl.zzpj.subscription_service.domain.token.decision;

import pl.zzpj.subscription_service.domain.subscription.PlanCode;

public record RejectedPlanNotFound(PlanCode planCode) implements TokenDecision {}
