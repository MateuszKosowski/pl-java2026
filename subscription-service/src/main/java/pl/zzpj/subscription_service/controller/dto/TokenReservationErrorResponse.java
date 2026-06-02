package pl.zzpj.subscription_service.controller.dto;

import pl.zzpj.subscription_service.domain.token.decision.TokenDecision;

public record TokenReservationErrorResponse(String code, String message) {

    public static TokenReservationErrorResponse from(String code, TokenDecision decision) {
        return new TokenReservationErrorResponse(code, TokenDecision.describe(decision));
    }
}
