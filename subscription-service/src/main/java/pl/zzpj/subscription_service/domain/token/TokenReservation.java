package pl.zzpj.subscription_service.domain.token;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TokenReservation(
    UUID reservationId, String userId, TokenOperation operation, int tokens, Instant expiresAt) {

  public TokenReservation {
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    Objects.requireNonNull(operation, "operation must not be null");
    if (tokens < 0) {
      throw new IllegalArgumentException("tokens must not be negative");
    }
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }
}
