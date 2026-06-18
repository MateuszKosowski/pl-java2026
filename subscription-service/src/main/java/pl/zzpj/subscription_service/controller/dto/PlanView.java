package pl.zzpj.subscription_service.controller.dto;

import java.util.Set;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.token.TokenOperation;

public record PlanView(PlanCode code, int monthlyTokens, Set<TokenOperation> allowedOperations) {

  public PlanView {
    allowedOperations = allowedOperations == null ? Set.of() : Set.copyOf(allowedOperations);
  }
}
