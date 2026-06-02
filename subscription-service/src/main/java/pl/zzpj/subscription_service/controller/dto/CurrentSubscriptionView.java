package pl.zzpj.subscription_service.controller.dto;

import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;

import java.time.Instant;

public record CurrentSubscriptionView(
        String userId,
        PlanCode planCode,
        Instant activeFrom,
        Instant activeUntil
) {

    public static CurrentSubscriptionView from(ActiveSubscription subscription) {
        return new CurrentSubscriptionView(
                subscription.userId(),
                subscription.planCode(),
                subscription.activeFrom(),
                subscription.activeUntil()
        );
    }
}
