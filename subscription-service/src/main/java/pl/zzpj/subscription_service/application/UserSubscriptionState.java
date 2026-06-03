package pl.zzpj.subscription_service.application;

import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.token.TokenBalance;

public record UserSubscriptionState(ActiveSubscription subscription, TokenBalance tokenBalance) {
}
