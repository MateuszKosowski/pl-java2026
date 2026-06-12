package pl.zzpj.subscription_service.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zzpj.subscription_service.domain.payment.PaymentOutcome;
import pl.zzpj.subscription_service.domain.payment.PaymentProvider;
import pl.zzpj.subscription_service.domain.payment.PaymentSession;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionCatalog;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;
import pl.zzpj.subscription_service.domain.token.TokenBalance;
import pl.zzpj.subscription_service.persistence.SubscriptionStore;

@Service
public class PaymentApplicationService {

    private final PaymentProvider paymentProvider;
    private final SubscriptionStore subscriptionStore;
    private final SubscriptionCatalog subscriptionCatalog;
    private final SubscriptionQueryService subscriptionQueryService;

    public PaymentApplicationService(
        PaymentProvider paymentProvider,
        SubscriptionStore subscriptionStore,
        SubscriptionCatalog subscriptionCatalog,
        SubscriptionQueryService subscriptionQueryService
    ) {
        this.paymentProvider = paymentProvider;
        this.subscriptionStore = subscriptionStore;
        this.subscriptionCatalog = subscriptionCatalog;
        this.subscriptionQueryService = subscriptionQueryService;
    }

    @Transactional
    public PaymentSession initiatePayment(String userId, PlanCode targetPlan) {
        return paymentProvider.createSession(userId, targetPlan);
    }

    @Transactional
    public PaymentSession completePayment(
        UUID sessionId,
        PaymentOutcome outcome
    ) {
        PaymentSession session = paymentProvider.completeSession(
            sessionId,
            outcome
        );

        switch (outcome) {
            case PaymentOutcome.Succeeded succeeded -> applyPaymentSuccess(
                session
            );
            case PaymentOutcome.Failed failed -> {
                /* No-op */
            }
            case PaymentOutcome.Cancelled cancelled -> {
                /* No-op */
            }
        }

        return session;
    }

    private void applyPaymentSuccess(PaymentSession session) {
        SubscriptionPlan plan = subscriptionCatalog
            .findPlan(session.targetPlan())
            .orElseThrow(() ->
                new IllegalStateException(
                    "Plan not found: " + session.targetPlan()
                )
            );

        UserSubscriptionState currentState = subscriptionQueryService.stateFor(
            session.userId()
        );

        ActiveSubscription updatedSubscription = new ActiveSubscription(
            session.userId(),
            session.targetPlan(),
            Instant.now(),
            null
        );

        TokenBalance updatedTokenBalance = new TokenBalance(
            session.userId(),
            plan.monthlyTokens(),
            currentState.tokenBalance().reservedTokens()
        );

        subscriptionStore.save(
            new UserSubscriptionState(updatedSubscription, updatedTokenBalance)
        );
    }
}
