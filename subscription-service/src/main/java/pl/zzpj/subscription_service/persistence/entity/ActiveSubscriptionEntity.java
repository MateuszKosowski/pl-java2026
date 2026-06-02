package pl.zzpj.subscription_service.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;

import java.time.Instant;

@Entity
@Table(name = "active_subscriptions")
public class ActiveSubscriptionEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false)
    private PlanCode planCode;

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom;

    @Column(name = "active_until")
    private Instant activeUntil;

    protected ActiveSubscriptionEntity() {
    }

    public ActiveSubscriptionEntity(String userId, PlanCode planCode, Instant activeFrom, Instant activeUntil) {
        this.userId = userId;
        this.planCode = planCode;
        this.activeFrom = activeFrom;
        this.activeUntil = activeUntil;
    }

    public static ActiveSubscriptionEntity from(ActiveSubscription subscription) {
        return new ActiveSubscriptionEntity(
                subscription.userId(),
                subscription.planCode(),
                subscription.activeFrom(),
                subscription.activeUntil()
        );
    }

    public ActiveSubscription toDomain() {
        return new ActiveSubscription(userId, planCode, activeFrom, activeUntil);
    }

    public String getUserId() {
        return userId;
    }
}
