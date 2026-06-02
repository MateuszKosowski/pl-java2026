package pl.zzpj.subscription_service.domain.subscription;

import java.time.Instant;
import java.util.Objects;

public record ActiveSubscription(
        String userId,
        PlanCode planCode,
        Instant activeFrom,
        Instant activeUntil
) {

    public ActiveSubscription {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Objects.requireNonNull(planCode, "planCode must not be null");
        Objects.requireNonNull(activeFrom, "activeFrom must not be null");
    }
}
