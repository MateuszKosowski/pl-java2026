package pl.zzpj.subscription_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.zzpj.subscription_service.config.properties.PlanDefinition;
import pl.zzpj.subscription_service.domain.token.TokenOperation;

import java.util.Map;

@ConfigurationProperties(prefix = "subscription")
public record SubscriptionProperties(
        Map<String, PlanDefinition> plans,
        Map<TokenOperation, Integer> tokenCosts
) {

    public SubscriptionProperties {
        plans = plans == null ? Map.of() : Map.copyOf(plans);
        tokenCosts = tokenCosts == null ? Map.of() : Map.copyOf(tokenCosts);
    }
}
