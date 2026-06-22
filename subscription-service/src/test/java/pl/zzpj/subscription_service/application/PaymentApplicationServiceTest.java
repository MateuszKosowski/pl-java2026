package pl.zzpj.subscription_service.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

  @Mock private PaymentProvider paymentProvider;

  @Mock private SubscriptionStore subscriptionStore;

  @Mock private SubscriptionCatalog subscriptionCatalog;

  @Mock private SubscriptionQueryService subscriptionQueryService;

  private final Instant now = Instant.parse("2026-06-18T12:00:00Z");
  private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

  private PaymentApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new PaymentApplicationService(
            paymentProvider,
            subscriptionStore,
            subscriptionCatalog,
            subscriptionQueryService,
            clock);
  }

  @Test
  void shouldRejectDowngradeWhenCreatingPaymentSession() {
    String userId = "user123";
    when(subscriptionQueryService.stateFor(userId)).thenReturn(state(userId, PlanCode.PRO, 100, 0));

    assertThrows(
        IllegalArgumentException.class, () -> service.initiatePayment(userId, PlanCode.STANDARD));
    verify(paymentProvider, never()).createSession(any(), any());
  }

  @Test
  void shouldAddPlanTokensAndStartNewMonthlyPeriodOnUpgrade() {
    String userId = "user123";
    UUID sessionId = UUID.randomUUID();
    PaymentSession session =
        new PaymentSession(
            sessionId,
            userId,
            PlanCode.PRO,
            PaymentSession.Status.SUCCEEDED,
            now.minusSeconds(10),
            now);
    UserSubscriptionState currentState = state(userId, PlanCode.STANDARD, 420, 3);

    when(paymentProvider.completeSession(
            sessionId, userId, new PaymentOutcome.Succeeded("transaction")))
        .thenReturn(new PaymentCompletion(session, true));
    when(subscriptionCatalog.findPlan(PlanCode.PRO))
        .thenReturn(Optional.of(new SubscriptionPlan(PlanCode.PRO, 2500, Set.of())));
    when(subscriptionStore.findForUpdate(userId)).thenReturn(Optional.of(currentState));
    when(subscriptionStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.completePayment(userId, sessionId, new PaymentOutcome.Succeeded("transaction"));

    ArgumentCaptor<UserSubscriptionState> stateCaptor =
        ArgumentCaptor.forClass(UserSubscriptionState.class);
    verify(subscriptionStore).save(stateCaptor.capture());
    UserSubscriptionState savedState = stateCaptor.getValue();
    assertEquals(PlanCode.PRO, savedState.subscription().planCode());
    assertEquals(now, savedState.subscription().activeFrom());
    assertEquals(Instant.parse("2026-07-18T12:00:00Z"), savedState.subscription().activeUntil());
    assertEquals(2920, savedState.tokenBalance().availableTokens());
    assertEquals(3, savedState.tokenBalance().reservedTokens());
  }

  @Test
  void shouldNotApplyTokensAgainForIdempotentCompletion() {
    String userId = "user123";
    UUID sessionId = UUID.randomUUID();
    PaymentSession session =
        new PaymentSession(
            sessionId,
            userId,
            PlanCode.PRO,
            PaymentSession.Status.SUCCEEDED,
            now.minusSeconds(10),
            now);
    PaymentOutcome outcome = new PaymentOutcome.Succeeded("transaction");
    when(paymentProvider.completeSession(sessionId, userId, outcome))
        .thenReturn(new PaymentCompletion(session, false));

    service.completePayment(userId, sessionId, outcome);

    verify(subscriptionStore, never()).save(any());
    verify(subscriptionCatalog, never()).findPlan(any());
  }

  private UserSubscriptionState state(
      String userId, PlanCode planCode, int availableTokens, int reservedTokens) {
    return new UserSubscriptionState(
        new ActiveSubscription(userId, planCode, now.minusSeconds(60), null),
        new TokenBalance(userId, availableTokens, reservedTokens));
  }
}
