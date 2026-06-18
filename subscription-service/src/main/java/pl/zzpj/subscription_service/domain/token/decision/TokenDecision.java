package pl.zzpj.subscription_service.domain.token.decision;

import pl.zzpj.subscription_service.domain.token.TokenReservation;

public sealed interface TokenDecision
    permits Accepted,
        RejectedInsufficientTokens,
        RejectedOperationNotAllowed,
        RejectedPlanNotFound {

  static String describe(TokenDecision decision) {
    return switch (decision) {
      case Accepted(
              TokenReservation(
                  var reservationId,
                  var userId,
                  var operation,
                  var tokens,
                  var expiresAt)) ->
          "Reserved "
              + tokens
              + " tokens for "
              + operation
              + " as "
              + reservationId
              + " for user "
              + userId;
      case RejectedInsufficientTokens(var operation, var requiredTokens, var availableTokens) ->
          "Cannot reserve "
              + requiredTokens
              + " tokens for "
              + operation
              + "; available tokens: "
              + availableTokens;
      case RejectedOperationNotAllowed(var planCode, var operation) ->
          "Plan " + planCode + " does not allow operation " + operation;
      case RejectedPlanNotFound(var planCode) -> "Subscription plan " + planCode + " was not found";
    };
  }
}
