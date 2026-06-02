package pl.zzpj.subscription_service.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.zzpj.subscription_service.persistence.entity.TokenBalanceEntity;

public interface TokenBalanceRepository extends JpaRepository<TokenBalanceEntity, String> {
}
