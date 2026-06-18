package pl.zzpj.subscription_service.domain.payment;

import java.util.UUID;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;

public interface PaymentProvider {
  PaymentSession createSession(String userId, PlanCode targetPlan);

  PaymentSession getSession(UUID sessionId);

  PaymentSession completeSession(UUID sessionId, PaymentOutcome outcome);
}
