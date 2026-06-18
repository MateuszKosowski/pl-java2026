package pl.zzpj.subscription_service.infrastructure.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.zzpj.subscription_service.domain.payment.PaymentOutcome;
import pl.zzpj.subscription_service.domain.payment.PaymentSession;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.persistence.entity.PaymentSessionEntity;
import pl.zzpj.subscription_service.persistence.repository.PaymentSessionRepository;

@ExtendWith(MockitoExtension.class)
class MockPaymentProviderTest {

  @Mock private PaymentSessionRepository repository;

  @Test
  void shouldReturnExistingResultForRepeatedSuccessfulCompletion() {
    UUID sessionId = UUID.randomUUID();
    PaymentSession succeededSession =
        session(sessionId, "user123", PaymentSession.Status.SUCCEEDED);
    when(repository.findByIdForUpdate(sessionId))
        .thenReturn(Optional.of(PaymentSessionEntity.from(succeededSession)));
    MockPaymentProvider provider = new MockPaymentProvider(repository);

    var completion =
        provider.completeSession(
            sessionId, "user123", new PaymentOutcome.Succeeded("another-request-id"));

    assertFalse(completion.completedNow());
    verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRejectCompletionByAnotherUser() {
    UUID sessionId = UUID.randomUUID();
    PaymentSession pendingSession = session(sessionId, "owner", PaymentSession.Status.PENDING);
    when(repository.findByIdForUpdate(sessionId))
        .thenReturn(Optional.of(PaymentSessionEntity.from(pendingSession)));
    MockPaymentProvider provider = new MockPaymentProvider(repository);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            provider.completeSession(
                sessionId, "other-user", new PaymentOutcome.Succeeded("transaction")));
    verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  private PaymentSession session(UUID sessionId, String userId, PaymentSession.Status status) {
    Instant now = Instant.parse("2026-06-18T12:00:00Z");
    return new PaymentSession(sessionId, userId, PlanCode.PRO, status, now.minusSeconds(10), now);
  }
}
