package pl.zzpj.subscription_service.domain.payment;

import pl.zzpj.subscription_service.domain.subscription.PlanCode;

import java.time.Instant;
import java.util.UUID;

public record PaymentSession(
        UUID id,
        String userId,
        PlanCode targetPlan,
        Status status,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        PENDING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public PaymentSession {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    public static PaymentSession create(String userId, PlanCode targetPlan) {
        return new PaymentSession(UUID.randomUUID(), userId, targetPlan, Status.PENDING, Instant.now(), Instant.now());
    }

    public PaymentSession succeed() {
        return new PaymentSession(id, userId, targetPlan, Status.SUCCEEDED, createdAt, Instant.now());
    }

    public PaymentSession fail() {
        return new PaymentSession(id, userId, targetPlan, Status.FAILED, createdAt, Instant.now());
    }

    public PaymentSession cancel() {
        return new PaymentSession(id, userId, targetPlan, Status.CANCELLED, createdAt, Instant.now());
    }
}
