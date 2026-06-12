package pl.zzpj.subscription_service.controller.dto.payment;

import pl.zzpj.subscription_service.domain.payment.PaymentSession;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;

import java.util.UUID;

public record PaymentSessionResponse(
        UUID id,
        String userId,
        PlanCode targetPlan,
        PaymentSession.Status status
) {
    public static PaymentSessionResponse from(PaymentSession session) {
        return new PaymentSessionResponse(
                session.id(),
                session.userId(),
                session.targetPlan(),
                session.status()
        );
    }
}
