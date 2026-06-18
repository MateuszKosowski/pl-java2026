package pl.zzpj.subscription_service.infrastructure.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.zzpj.subscription_service.domain.payment.PaymentCompletion;
import pl.zzpj.subscription_service.domain.payment.PaymentOutcome;
import pl.zzpj.subscription_service.domain.payment.PaymentProvider;
import pl.zzpj.subscription_service.domain.payment.PaymentSession;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.persistence.entity.PaymentSessionEntity;
import pl.zzpj.subscription_service.persistence.repository.PaymentSessionRepository;

@Component
public class MockPaymentProvider implements PaymentProvider {

  private final PaymentSessionRepository repository;

  public MockPaymentProvider(PaymentSessionRepository repository) {
    this.repository = repository;
  }

  @Override
  public PaymentSession createSession(String userId, PlanCode targetPlan) {
    PaymentSession session = PaymentSession.create(userId, targetPlan);
    repository.save(PaymentSessionEntity.from(session));
    return session;
  }

  @Override
  public PaymentSession getSession(UUID sessionId) {
    return repository
        .findById(sessionId)
        .map(PaymentSessionEntity::toDomain)
        .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
  }

  @Override
  public PaymentCompletion completeSession(UUID sessionId, String userId, PaymentOutcome outcome) {
    PaymentSession session =
        repository
            .findByIdForUpdate(sessionId)
            .map(PaymentSessionEntity::toDomain)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    if (!session.userId().equals(userId)) {
      throw new IllegalArgumentException("Payment session belongs to another user");
    }
    if (session.status() != PaymentSession.Status.PENDING) {
      if (session.status() == statusFor(outcome)) {
        return new PaymentCompletion(session, false);
      }
      throw new IllegalStateException("Session already completed with status " + session.status());
    }

    PaymentSession updatedSession =
        switch (outcome) {
          case PaymentOutcome.Succeeded succeeded -> session.succeed();
          case PaymentOutcome.Failed failed -> session.fail();
          case PaymentOutcome.Cancelled cancelled -> session.cancel();
        };

    repository.save(PaymentSessionEntity.from(updatedSession));
    return new PaymentCompletion(updatedSession, true);
  }

  private PaymentSession.Status statusFor(PaymentOutcome outcome) {
    return switch (outcome) {
      case PaymentOutcome.Succeeded succeeded -> PaymentSession.Status.SUCCEEDED;
      case PaymentOutcome.Failed failed -> PaymentSession.Status.FAILED;
      case PaymentOutcome.Cancelled cancelled -> PaymentSession.Status.CANCELLED;
    };
  }
}
