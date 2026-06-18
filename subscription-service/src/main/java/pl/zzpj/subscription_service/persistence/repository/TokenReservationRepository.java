package pl.zzpj.subscription_service.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.zzpj.subscription_service.persistence.entity.TokenReservationEntity;

public interface TokenReservationRepository extends JpaRepository<TokenReservationEntity, UUID> {}
