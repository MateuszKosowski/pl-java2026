package pl.zzpj.subscription_service.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zzpj.subscription_service.domain.payment.PaymentCompletion;
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
  private final Clock clock;

  public PaymentApplicationService(
      PaymentProvider paymentProvider,
      SubscriptionStore subscriptionStore,
      SubscriptionCatalog subscriptionCatalog,
      SubscriptionQueryService subscriptionQueryService,
      Clock clock) {
    this.paymentProvider = paymentProvider;
    this.subscriptionStore = subscriptionStore;
    this.subscriptionCatalog = subscriptionCatalog;
    this.subscriptionQueryService = subscriptionQueryService;
    this.clock = clock;
  }

  @Transactional
  public PaymentSession initiatePayment(String userId, PlanCode targetPlan) {
    UserSubscriptionState currentState = subscriptionQueryService.stateFor(userId);
    ensureSubscriptionReadyForUpgrade(currentState);
    ensureUpgradeAllowed(currentState.subscription().planCode(), targetPlan);
    return paymentProvider.createSession(userId, targetPlan);
  }

  @Transactional
  public PaymentSession completePayment(String userId, UUID sessionId, PaymentOutcome outcome) {
    PaymentCompletion completion = paymentProvider.completeSession(sessionId, userId, outcome);
    PaymentSession session = completion.session();

    if (!completion.completedNow()) {
      return session;
    }

    switch (outcome) {
      case PaymentOutcome.Succeeded succeeded -> applyPaymentSuccess(session);
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
    SubscriptionPlan plan =
        subscriptionCatalog
            .findPlan(session.targetPlan())
            .orElseThrow(
                () -> new IllegalStateException("Plan not found: " + session.targetPlan()));

    subscriptionQueryService.stateFor(session.userId());
    UserSubscriptionState currentState =
        subscriptionStore
            .findForUpdate(session.userId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Subscription not found for user " + session.userId()));
    ensureSubscriptionReadyForUpgrade(currentState);
    ensureUpgradeAllowed(currentState.subscription().planCode(), session.targetPlan());

    Instant now = clock.instant();

    ActiveSubscription updatedSubscription =
        new ActiveSubscription(
            session.userId(),
            session.targetPlan(),
            now,
            now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());

    TokenBalance updatedTokenBalance =
        new TokenBalance(
            session.userId(),
            Math.addExact(currentState.tokenBalance().availableTokens(), plan.monthlyTokens()),
            currentState.tokenBalance().reservedTokens());

    subscriptionStore.save(new UserSubscriptionState(updatedSubscription, updatedTokenBalance));
  }

  private void ensureUpgradeAllowed(PlanCode currentPlan, PlanCode targetPlan) {
    if (!currentPlan.canUpgradeTo(targetPlan)) {
      throw new IllegalArgumentException(
          "Plan change from " + currentPlan + " to " + targetPlan + " is not an upgrade");
    }
  }

  private void ensureSubscriptionReadyForUpgrade(UserSubscriptionState currentState) {
    if (currentState.subscription().isExpiredAt(clock.instant())) {
      throw new IllegalStateException("Expired subscription still has pending token reservations");
    }
  }
}
