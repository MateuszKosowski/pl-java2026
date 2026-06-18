package pl.zzpj.subscription_service.domain.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pl.zzpj.subscription_service.domain.pricing.PricingCatalog;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionCatalog;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;
import pl.zzpj.subscription_service.domain.token.decision.Accepted;
import pl.zzpj.subscription_service.domain.token.decision.RejectedInsufficientTokens;
import pl.zzpj.subscription_service.domain.token.decision.RejectedOperationNotAllowed;
import pl.zzpj.subscription_service.domain.token.decision.RejectedPlanNotFound;
import pl.zzpj.subscription_service.domain.token.decision.RejectedSubscriptionExpired;
import pl.zzpj.subscription_service.domain.token.decision.TokenDecision;

public class TokenReservationPolicy {

  private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofMinutes(15);

  private final SubscriptionCatalog subscriptionCatalog;
  private final PricingCatalog pricingCatalog;
  private final Duration reservationTtl;

  public TokenReservationPolicy(
      SubscriptionCatalog subscriptionCatalog, PricingCatalog pricingCatalog) {
    this(subscriptionCatalog, pricingCatalog, DEFAULT_RESERVATION_TTL);
  }

  public TokenReservationPolicy(
      SubscriptionCatalog subscriptionCatalog,
      PricingCatalog pricingCatalog,
      Duration reservationTtl) {
    this.subscriptionCatalog =
        Objects.requireNonNull(subscriptionCatalog, "subscriptionCatalog must not be null");
    this.pricingCatalog = Objects.requireNonNull(pricingCatalog, "pricingCatalog must not be null");
    this.reservationTtl = Objects.requireNonNull(reservationTtl, "reservationTtl must not be null");
  }

  public TokenDecision decide(
      ActiveSubscription subscription,
      TokenBalance balance,
      TokenOperation operation,
      Instant now) {
    Objects.requireNonNull(subscription, "subscription must not be null");
    Objects.requireNonNull(balance, "balance must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (subscription.isExpiredAt(now)) {
      return new RejectedSubscriptionExpired(subscription.activeUntil());
    }

    return subscriptionCatalog
        .findPlan(subscription.planCode())
        .map(plan -> decideForPlan(plan, subscription, balance, operation, now))
        .orElseGet(() -> new RejectedPlanNotFound(subscription.planCode()));
  }

  private TokenDecision decideForPlan(
      SubscriptionPlan plan,
      ActiveSubscription subscription,
      TokenBalance balance,
      TokenOperation operation,
      Instant now) {
    if (!plan.allows(operation)) {
      return new RejectedOperationNotAllowed(plan.code(), operation);
    }

    int cost = pricingCatalog.costOf(operation);
    if (!balance.canReserve(cost)) {
      return new RejectedInsufficientTokens(operation, cost, balance.availableTokens());
    }

    return new Accepted(
        new TokenReservation(
            UUID.randomUUID(), subscription.userId(), operation, cost, now.plus(reservationTtl)));
  }
}
