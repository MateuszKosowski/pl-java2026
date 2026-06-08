package pl.zzpj.subscription_service.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.zzpj.subscription_service.persistence.entity.TokenBalanceEntity;

public interface TokenBalanceRepository extends JpaRepository<TokenBalanceEntity, String> {

    @Modifying
    @Query(value = """
            INSERT INTO subscription_schema.token_balances (user_id, available_tokens, reserved_tokens)
            VALUES (:userId, :availableTokens, :reservedTokens)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(
            @Param("userId") String userId,
            @Param("availableTokens") int availableTokens,
            @Param("reservedTokens") int reservedTokens
    );
}
