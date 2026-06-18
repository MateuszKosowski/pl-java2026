package pl.zzpj.subscription_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import pl.zzpj.subscription_service.application.TokenReservationCommandService;
import pl.zzpj.subscription_service.application.UserIdentityResolver;
import pl.zzpj.subscription_service.application.command.CreateTokenReservationCommand;
import pl.zzpj.subscription_service.controller.dto.CreateTokenReservationRequest;
import pl.zzpj.subscription_service.controller.dto.TokenReservationErrorResponse;
import pl.zzpj.subscription_service.controller.dto.TokenReservationResponse;
import pl.zzpj.subscription_service.domain.token.TokenReservation;
import pl.zzpj.subscription_service.domain.token.decision.Accepted;
import pl.zzpj.subscription_service.domain.token.decision.RejectedInsufficientTokens;
import pl.zzpj.subscription_service.domain.token.decision.RejectedOperationNotAllowed;
import pl.zzpj.subscription_service.domain.token.decision.RejectedPlanNotFound;
import pl.zzpj.subscription_service.domain.token.decision.TokenDecision;
import pl.zzpj.subscription_service.persistence.entity.TokenReservationEntity;

@Tag(name = "Token Reservation", description = "API for managing token reservations for operations")
public class TokenReservationController {

  private final TokenReservationCommandService reservationCommandService;
  private final UserIdentityResolver userIdentityResolver;

  public TokenReservationController(
      TokenReservationCommandService reservationCommandService,
      UserIdentityResolver userIdentityResolver) {
    this.reservationCommandService = reservationCommandService;
    this.userIdentityResolver = userIdentityResolver;
  }

  @PostMapping
  @Operation(
      summary = "Reserve tokens",
      description = "Creates a new token reservation for a specific operation.")
  public ResponseEntity<?> reserve(
      Principal principal, @RequestBody CreateTokenReservationRequest request) {
    String userId = userIdentityResolver.resolve(principal);
    TokenDecision decision =
        reservationCommandService.reserve(
            userId,
            new CreateTokenReservationCommand(request.operation(), request.externalOperationId()));
    return toResponse(decision);
  }

  @PostMapping("/{reservationId}/consume")
  @Operation(
      summary = "Consume reservation",
      description = "Finalizes a token reservation, deducting tokens from the balance.")
  public TokenReservationResponse consume(Principal principal, @PathVariable UUID reservationId) {
    String userId = userIdentityResolver.resolve(principal);
    TokenReservationEntity reservation = reservationCommandService.consume(userId, reservationId);
    return TokenReservationResponse.from(reservation);
  }

  @PostMapping("/{reservationId}/release")
  @Operation(
      summary = "Release reservation",
      description = "Cancels a token reservation, returning tokens to the balance.")
  public TokenReservationResponse release(Principal principal, @PathVariable UUID reservationId) {
    String userId = userIdentityResolver.resolve(principal);
    TokenReservationEntity reservation = reservationCommandService.release(userId, reservationId);
    return TokenReservationResponse.from(reservation);
  }

  private ResponseEntity<?> toResponse(TokenDecision decision) {
    return switch (decision) {
      case Accepted(TokenReservation reservation) ->
          ResponseEntity.status(HttpStatus.CREATED)
              .body(TokenReservationResponse.from(reservation));
      case RejectedInsufficientTokens rejected ->
          ResponseEntity.status(HttpStatus.CONFLICT)
              .body(TokenReservationErrorResponse.from("INSUFFICIENT_TOKENS", rejected));
      case RejectedOperationNotAllowed rejected ->
          ResponseEntity.status(HttpStatus.FORBIDDEN)
              .body(TokenReservationErrorResponse.from("OPERATION_NOT_ALLOWED", rejected));
      case RejectedPlanNotFound rejected ->
          ResponseEntity.status(HttpStatus.CONFLICT)
              .body(TokenReservationErrorResponse.from("PLAN_NOT_FOUND", rejected));
    };
  }
}
