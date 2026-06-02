package pl.zzpj.subscription_service.controller.dto;

import pl.zzpj.subscription_service.domain.token.TokenBalance;

public record TokenBalanceView(
        String userId,
        int availableTokens,
        int reservedTokens
) {

    public static TokenBalanceView from(TokenBalance tokenBalance) {
        return new TokenBalanceView(
                tokenBalance.userId(),
                tokenBalance.availableTokens(),
                tokenBalance.reservedTokens()
        );
    }
}
