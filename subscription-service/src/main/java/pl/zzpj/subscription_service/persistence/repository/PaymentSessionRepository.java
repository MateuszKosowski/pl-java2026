package pl.zzpj.subscription_service.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.zzpj.subscription_service.persistence.entity.PaymentSessionEntity;

import java.util.UUID;

public interface PaymentSessionRepository extends JpaRepository<PaymentSessionEntity, UUID> {
}
