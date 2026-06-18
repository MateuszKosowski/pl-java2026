package pl.zzpj.subscription_service.domain.token.decision;

import java.time.Instant;

public record RejectedSubscriptionExpired(Instant expiredAt) implements TokenDecision {}
