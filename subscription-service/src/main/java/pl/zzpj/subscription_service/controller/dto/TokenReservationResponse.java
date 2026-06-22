package pl.zzpj.subscription_service.controller.dto;

import java.time.Instant;
import java.util.UUID;
import pl.zzpj.subscription_service.domain.token.TokenOperation;
import pl.zzpj.subscription_service.domain.token.TokenReservation;
import pl.zzpj.subscription_service.domain.token.reservation.TokenReservationStatus;
import pl.zzpj.subscription_service.persistence.entity.TokenReservationEntity;

public record TokenReservationResponse(
    UUID reservationId,
    String userId,
    TokenOperation operation,
    int tokens,
    TokenReservationStatus status,
    Instant expiresAt) {

  public static TokenReservationResponse from(TokenReservation reservation) {
    return new TokenReservationResponse(
        reservation.reservationId(),
        reservation.userId(),
        reservation.operation(),
        reservation.tokens(),
        TokenReservationStatus.RESERVED,
        reservation.expiresAt());
  }

  public static TokenReservationResponse from(TokenReservationEntity reservation) {
    return new TokenReservationResponse(
        reservation.getId(),
        reservation.getUserId(),
        reservation.getOperation(),
        reservation.getTokens(),
        reservation.getStatus(),
        reservation.getExpiresAt());
  }
}
