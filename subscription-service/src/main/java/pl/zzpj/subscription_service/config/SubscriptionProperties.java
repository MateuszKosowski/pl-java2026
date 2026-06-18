package pl.zzpj.subscription_service.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.zzpj.subscription_service.config.properties.PlanDefinition;
import pl.zzpj.subscription_service.domain.token.TokenOperation;

@ConfigurationProperties(prefix = "subscription")
public record SubscriptionProperties(
    Map<String, PlanDefinition> plans, Map<TokenOperation, Integer> tokenCosts) {

  public SubscriptionProperties {
    plans = plans == null ? Map.of() : Map.copyOf(plans);
    tokenCosts = tokenCosts == null ? Map.of() : Map.copyOf(tokenCosts);
  }
}
