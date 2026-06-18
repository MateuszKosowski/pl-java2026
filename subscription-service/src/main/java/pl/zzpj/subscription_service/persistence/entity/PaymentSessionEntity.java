package pl.zzpj.subscription_service.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import pl.zzpj.subscription_service.domain.payment.PaymentSession;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;

@Entity
@Table(name = "payment_sessions", schema = "subscription_schema")
public class PaymentSessionEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_plan", nullable = false)
  private PlanCode targetPlan;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PaymentSession.Status status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PaymentSessionEntity() {}

  public PaymentSessionEntity(
      UUID id,
      String userId,
      PlanCode targetPlan,
      PaymentSession.Status status,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.userId = userId;
    this.targetPlan = targetPlan;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static PaymentSessionEntity from(PaymentSession session) {
    return new PaymentSessionEntity(
        session.id(),
        session.userId(),
        session.targetPlan(),
        session.status(),
        session.createdAt(),
        session.updatedAt());
  }

  public PaymentSession toDomain() {
    return new PaymentSession(id, userId, targetPlan, status, createdAt, updatedAt);
  }

  public UUID getId() {
    return id;
  }
}
