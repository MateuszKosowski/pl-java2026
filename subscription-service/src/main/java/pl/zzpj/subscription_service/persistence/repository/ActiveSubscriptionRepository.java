package pl.zzpj.subscription_service.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.zzpj.subscription_service.persistence.entity.ActiveSubscriptionEntity;

public interface ActiveSubscriptionRepository
    extends JpaRepository<ActiveSubscriptionEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select subscription from ActiveSubscriptionEntity subscription where subscription.userId ="
          + " :userId")
  Optional<ActiveSubscriptionEntity> findByIdForUpdate(@Param("userId") String userId);

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
