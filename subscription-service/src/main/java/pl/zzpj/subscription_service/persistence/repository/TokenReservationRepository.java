package pl.zzpj.subscription_service.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.zzpj.subscription_service.persistence.entity.TokenReservationEntity;

import java.util.UUID;

public interface TokenReservationRepository extends JpaRepository<TokenReservationEntity, UUID> {
}
