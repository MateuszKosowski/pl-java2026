package pl.zzpj.subscription_service.domain.token.decision;

import pl.zzpj.subscription_service.domain.token.TokenReservation;

public record Accepted(TokenReservation reservation) implements TokenDecision {
}
