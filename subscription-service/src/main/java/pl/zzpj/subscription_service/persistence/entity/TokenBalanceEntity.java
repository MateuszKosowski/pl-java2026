package pl.zzpj.subscription_service.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pl.zzpj.subscription_service.domain.token.TokenBalance;

@Entity
@Table(name = "token_balances")
public class TokenBalanceEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "available_tokens", nullable = false)
    private int availableTokens;

    @Column(name = "reserved_tokens", nullable = false)
    private int reservedTokens;

    protected TokenBalanceEntity() {
    }

    public TokenBalanceEntity(String userId, int availableTokens, int reservedTokens) {
        this.userId = userId;
        this.availableTokens = availableTokens;
        this.reservedTokens = reservedTokens;
    }

    public static TokenBalanceEntity from(TokenBalance tokenBalance) {
        return new TokenBalanceEntity(
                tokenBalance.userId(),
                tokenBalance.availableTokens(),
                tokenBalance.reservedTokens()
        );
    }

    public TokenBalance toDomain() {
        return new TokenBalance(userId, availableTokens, reservedTokens);
    }

    public String getUserId() {
        return userId;
    }
}
