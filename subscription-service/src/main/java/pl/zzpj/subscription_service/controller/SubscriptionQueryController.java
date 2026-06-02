package pl.zzpj.subscription_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.zzpj.subscription_service.application.SubscriptionQueryService;
import pl.zzpj.subscription_service.application.UserIdentityResolver;
import pl.zzpj.subscription_service.application.UserSubscriptionState;
import pl.zzpj.subscription_service.controller.dto.CurrentSubscriptionView;
import pl.zzpj.subscription_service.controller.dto.PlanView;
import pl.zzpj.subscription_service.controller.dto.TokenBalanceView;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionQueryController {

    private final SubscriptionQueryService subscriptionQueryService;
    private final UserIdentityResolver userIdentityResolver;

    public SubscriptionQueryController(
            SubscriptionQueryService subscriptionQueryService,
            UserIdentityResolver userIdentityResolver
    ) {
        this.subscriptionQueryService = subscriptionQueryService;
        this.userIdentityResolver = userIdentityResolver;
    }

    @GetMapping("/plans")
    public List<PlanView> plans() {
        return subscriptionQueryService.availablePlans().stream()
                .map(this::toPlanView)
                .toList();
    }

    @GetMapping("/me")
    public CurrentSubscriptionView currentSubscription(Principal principal) {
        String userId = userIdentityResolver.resolve(principal);
        UserSubscriptionState state = subscriptionQueryService.stateFor(userId);
        return CurrentSubscriptionView.from(state.subscription());
    }

    @GetMapping("/me/tokens")
    public TokenBalanceView tokenBalance(Principal principal) {
        String userId = userIdentityResolver.resolve(principal);
        UserSubscriptionState state = subscriptionQueryService.stateFor(userId);
        return TokenBalanceView.from(state.tokenBalance());
    }

    private PlanView toPlanView(SubscriptionPlan plan) {
        return new PlanView(plan.code(), plan.monthlyTokens(), plan.allowedOperations());
    }
}
