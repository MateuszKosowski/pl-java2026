package pl.zzpj.subscription_service.controller.dto;

import pl.zzpj.subscription_service.domain.token.TokenOperation;

public record CreateTokenReservationRequest(TokenOperation operation, String externalOperationId) {
}
