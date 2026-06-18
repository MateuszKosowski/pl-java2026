package pl.zzpj.subscription_service.config;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.zzpj.subscription_service.domain.pricing.PricingCatalog;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionCatalog;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;
import pl.zzpj.subscription_service.domain.token.TokenReservationPolicy;

@Configuration
public class SubscriptionDomainConfig {

  @Bean
  public PricingCatalog pricingCatalog(SubscriptionProperties properties) {
    return new PricingCatalog(properties.tokenCosts());
  }

  @Bean
  public SubscriptionCatalog subscriptionCatalog(SubscriptionProperties properties) {
    Map<PlanCode, SubscriptionPlan> plans =
        properties.plans().entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    entry -> PlanCode.valueOf(entry.getKey().toUpperCase(Locale.ROOT)),
                    entry ->
                        new SubscriptionPlan(
                            PlanCode.valueOf(entry.getKey().toUpperCase(Locale.ROOT)),
                            entry.getValue().monthlyTokens(),
                            entry.getValue().allowedOperations())));
    return new SubscriptionCatalog(plans);
  }

  @Bean
  public TokenReservationPolicy tokenReservationPolicy(
      SubscriptionCatalog subscriptionCatalog, PricingCatalog pricingCatalog) {
    return new TokenReservationPolicy(subscriptionCatalog, pricingCatalog);
  }
}
