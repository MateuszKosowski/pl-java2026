package pl.zzpj.subscription_service.persistence.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.zzpj.subscription_service.persistence.entity.ActiveSubscriptionEntity;

public interface ActiveSubscriptionRepository
    extends JpaRepository<ActiveSubscriptionEntity, String> {

  @Modifying
  @Query(
      value =
          """
            INSERT INTO subscription_schema.active_subscriptions (user_id, plan_code, active_from, active_until)
            VALUES (:userId, :planCode, :activeFrom, NULL)
            ON CONFLICT (user_id) DO NOTHING
            """,
      nativeQuery = true)
  void insertIfMissing(
      @Param("userId") String userId,
      @Param("planCode") String planCode,
      @Param("activeFrom") Instant activeFrom);
}
