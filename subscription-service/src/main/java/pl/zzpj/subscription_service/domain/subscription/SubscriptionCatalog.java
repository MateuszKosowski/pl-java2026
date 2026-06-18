package pl.zzpj.subscription_service.domain.subscription;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SubscriptionCatalog(Map<PlanCode, SubscriptionPlan> plans) {

  public SubscriptionCatalog {
    plans = plans == null ? Map.of() : Map.copyOf(plans);
    plans.forEach(
        (code, plan) -> {
          Objects.requireNonNull(code, "plan code must not be null");
          Objects.requireNonNull(plan, "plan must not be null");
        });
  }

  public Optional<SubscriptionPlan> findPlan(PlanCode code) {
    return Optional.ofNullable(plans.get(code));
  }
}
