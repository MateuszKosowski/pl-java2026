package pl.zzpj.subscription_service.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.zzpj.subscription_service.persistence.entity.ActiveSubscriptionEntity;

public interface ActiveSubscriptionRepository extends JpaRepository<ActiveSubscriptionEntity, String> {
}
