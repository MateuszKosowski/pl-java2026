package pl.zzpj.subscription_service.infrastructure.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;
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
  public PaymentSession completeSession(UUID sessionId, PaymentOutcome outcome) {
    PaymentSession session = getSession(sessionId);
    if (session.status() != PaymentSession.Status.PENDING) {
      throw new IllegalStateException("Session already completed: " + sessionId);
    }

    PaymentSession updatedSession =
        switch (outcome) {
          case PaymentOutcome.Succeeded succeeded -> session.succeed();
          case PaymentOutcome.Failed failed -> session.fail();
          case PaymentOutcome.Cancelled cancelled -> session.cancel();
        };

    repository.save(PaymentSessionEntity.from(updatedSession));
    return updatedSession;
  }
}
