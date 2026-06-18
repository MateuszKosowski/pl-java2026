package pl.zzpj.subscription_service.controller.dto.payment;

import java.util.UUID;
import pl.zzpj.subscription_service.domain.payment.PaymentSession;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;

public record PaymentSessionResponse(
    UUID id, String userId, PlanCode targetPlan, PaymentSession.Status status) {
  public static PaymentSessionResponse from(PaymentSession session) {
    return new PaymentSessionResponse(
        session.id(), session.userId(), session.targetPlan(), session.status());
  }
}
