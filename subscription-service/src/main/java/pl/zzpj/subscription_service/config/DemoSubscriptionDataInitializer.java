package pl.zzpj.subscription_service.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pl.zzpj.subscription_service.application.UserSubscriptionState;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionCatalog;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;
import pl.zzpj.subscription_service.domain.token.TokenBalance;
import pl.zzpj.subscription_service.persistence.SubscriptionStore;

import java.time.Instant;
import java.util.List;

@Component
public class DemoSubscriptionDataInitializer implements CommandLineRunner {

    private final SubscriptionCatalog subscriptionCatalog;
    private final SubscriptionStore subscriptionStore;

    public DemoSubscriptionDataInitializer(
            SubscriptionCatalog subscriptionCatalog,
            SubscriptionStore subscriptionStore
    ) {
        this.subscriptionCatalog = subscriptionCatalog;
        this.subscriptionStore = subscriptionStore;
    }

    @Override
    public void run(String... args) {
        Instant now = Instant.now();
        demoUsers().forEach(user -> seedUser(user, now));
    }

    private void seedUser(DemoUser user, Instant now) {
        if (subscriptionStore.find(user.userId()).isPresent()) {
            return;
        }

        SubscriptionPlan plan = subscriptionCatalog.findPlan(user.planCode())
                .orElseThrow(() -> new IllegalStateException("Demo plan " + user.planCode() + " is not configured"));

        subscriptionStore.save(new UserSubscriptionState(
                new ActiveSubscription(user.userId(), plan.code(), now, null),
                new TokenBalance(user.userId(), user.availableTokens(), user.reservedTokens())
        ));
    }

    private List<DemoUser> demoUsers() {
        return List.of(
                new DemoUser("admin-1", PlanCode.PRO, 2500, 0),
                new DemoUser("free-2", PlanCode.FREE, 50, 0),
                new DemoUser("standard-3", PlanCode.STANDARD, 500, 0),
                new DemoUser("pro-4", PlanCode.PRO, 2500, 0),
                new DemoUser("lowbalance-5", PlanCode.FREE, 3, 0)
        );
    }
}
