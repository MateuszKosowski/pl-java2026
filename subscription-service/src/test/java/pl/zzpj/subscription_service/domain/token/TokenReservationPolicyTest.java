package pl.zzpj.subscription_service.domain.token;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.zzpj.subscription_service.domain.pricing.PricingCatalog;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionCatalog;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;
import pl.zzpj.subscription_service.domain.token.decision.RejectedSubscriptionExpired;

class TokenReservationPolicyTest {

  @Test
  void shouldRejectNewReservationForExpiredSubscription() {
    Instant now = Instant.parse("2026-06-18T12:00:00Z");
    SubscriptionPlan pro = new SubscriptionPlan(PlanCode.PRO, 2500, Set.of(TokenOperation.DETECT));
    TokenReservationPolicy policy =
        new TokenReservationPolicy(
            new SubscriptionCatalog(Map.of(PlanCode.PRO, pro)),
            new PricingCatalog(Map.of(TokenOperation.DETECT, 1)));
    ActiveSubscription expiredSubscription =
        new ActiveSubscription(
            "user123", PlanCode.PRO, now.minusSeconds(2_000_000), now.minusSeconds(1));

    var decision =
        policy.decide(
            expiredSubscription, new TokenBalance("user123", 100, 1), TokenOperation.DETECT, now);

    assertInstanceOf(RejectedSubscriptionExpired.class, decision);
  }
}
