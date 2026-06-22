package pl.zzpj.subscription_service.domain.token;

public record TokenBalance(String userId, int availableTokens, int reservedTokens) {

  public TokenBalance {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    if (availableTokens < 0) {
      throw new IllegalArgumentException("availableTokens must not be negative");
    }
    if (reservedTokens < 0) {
      throw new IllegalArgumentException("reservedTokens must not be negative");
    }
  }

  public boolean canReserve(int tokens) {
    return tokens >= 0 && availableTokens >= tokens;
  }

  public TokenBalance reserve(int tokens) {
    if (!canReserve(tokens)) {
      throw new IllegalArgumentException("Not enough available tokens");
    }
    return new TokenBalance(userId, availableTokens - tokens, reservedTokens + tokens);
  }

  public TokenBalance consumeReserved(int tokens) {
    if (tokens < 0 || reservedTokens < tokens) {
      throw new IllegalArgumentException("Not enough reserved tokens");
    }
    return new TokenBalance(userId, availableTokens, reservedTokens - tokens);
  }

  public TokenBalance releaseReserved(int tokens) {
    if (tokens < 0 || reservedTokens < tokens) {
      throw new IllegalArgumentException("Not enough reserved tokens");
    }
    return new TokenBalance(userId, availableTokens + tokens, reservedTokens - tokens);
  }
}
