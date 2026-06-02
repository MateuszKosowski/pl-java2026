package pl.zzpj.subscription_service.application;

import org.springframework.stereotype.Service;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionCatalog;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;
import pl.zzpj.subscription_service.domain.token.TokenBalance;
import pl.zzpj.subscription_service.persistence.SubscriptionStore;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class SubscriptionQueryService {

    private static final PlanCode DEFAULT_PLAN = PlanCode.FREE;

    private final SubscriptionCatalog subscriptionCatalog;
    private final SubscriptionStore subscriptionStore;

    public SubscriptionQueryService(
            SubscriptionCatalog subscriptionCatalog,
            SubscriptionStore subscriptionStore
    ) {
        this.subscriptionCatalog = subscriptionCatalog;
        this.subscriptionStore = subscriptionStore;
    }

    public List<SubscriptionPlan> availablePlans() {
        return subscriptionCatalog.plans().values().stream()
                .sorted(Comparator.comparing(plan -> plan.code().ordinal()))
                .toList();
    }

    public UserSubscriptionState stateFor(String userId) {
        SubscriptionPlan defaultPlan = subscriptionCatalog.findPlan(DEFAULT_PLAN)
                .orElseThrow(() -> new IllegalStateException("Default plan " + DEFAULT_PLAN + " is not configured"));

        UserSubscriptionState initialState = new UserSubscriptionState(
                new ActiveSubscription(userId, defaultPlan.code(), Instant.now(), null),
                new TokenBalance(userId, defaultPlan.monthlyTokens(), 0)
        );

        return subscriptionStore.getOrCreate(userId, initialState);
    }
}
