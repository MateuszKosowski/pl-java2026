package pl.zzpj.subscription_service.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.zzpj.subscription_service.persistence.entity.PaymentSessionEntity;

public interface PaymentSessionRepository extends JpaRepository<PaymentSessionEntity, UUID> {}
