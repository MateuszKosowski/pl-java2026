package pl.zzpj.subscription_service.application.command;

import pl.zzpj.subscription_service.domain.token.TokenOperation;

public record CreateTokenReservationCommand(TokenOperation operation, String externalOperationId) {

    public CreateTokenReservationCommand {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
    }
}
