package pl.zzpj.subscription_service.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.zzpj.subscription_service.persistence.entity.PaymentSessionEntity;

public interface PaymentSessionRepository extends JpaRepository<PaymentSessionEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select session from PaymentSessionEntity session where session.id = :id")
  Optional<PaymentSessionEntity> findByIdForUpdate(@Param("id") UUID id);
}
