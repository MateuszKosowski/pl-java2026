package pl.zzpj.subscription_service.config.properties;

import java.util.Set;
import pl.zzpj.subscription_service.domain.token.TokenOperation;

public record PlanDefinition(int monthlyTokens, Set<TokenOperation> allowedOperations) {

  public PlanDefinition {
    allowedOperations = allowedOperations == null ? Set.of() : Set.copyOf(allowedOperations);
  }
}
