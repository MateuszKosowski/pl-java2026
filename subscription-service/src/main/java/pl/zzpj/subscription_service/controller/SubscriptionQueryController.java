package pl.zzpj.subscription_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
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

@RestController
@RequestMapping("/api/subscriptions")
@Tag(
    name = "Subscription Query",
    description = "API for querying user subscriptions and plans"
)
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
    @Operation(
        summary = "List available plans",
        description = "Returns a list of all available subscription plans."
    )
    public List<PlanView> plans() {
        return subscriptionQueryService
            .availablePlans()
            .stream()
            .map(this::toPlanView)
            .toList();
    }

    @GetMapping("/me")
    @Operation(
        summary = "Current subscription",
        description = "Returns details of the current user's active subscription."
    )
    public CurrentSubscriptionView currentSubscription(Principal principal) {
        String userId = userIdentityResolver.resolve(principal);
        UserSubscriptionState state = subscriptionQueryService.stateFor(userId);
        return CurrentSubscriptionView.from(state.subscription());
    }

    @GetMapping("/me/tokens")
    @Operation(
        summary = "Token balance",
        description = "Returns the remaining token balance for the current user."
    )
    public TokenBalanceView tokenBalance(Principal principal) {
        String userId = userIdentityResolver.resolve(principal);
        UserSubscriptionState state = subscriptionQueryService.stateFor(userId);
        return TokenBalanceView.from(state.tokenBalance());
    }

    private PlanView toPlanView(SubscriptionPlan plan) {
        return new PlanView(
            plan.code(),
            plan.monthlyTokens(),
            plan.allowedOperations()
        );
    }
}
