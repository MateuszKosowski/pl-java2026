package pl.zzpj.subscription_service.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zzpj.subscription_service.application.command.CreateTokenReservationCommand;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.token.TokenBalance;
import pl.zzpj.subscription_service.domain.token.TokenOperation;
import pl.zzpj.subscription_service.domain.token.TokenReservationPolicy;
import pl.zzpj.subscription_service.domain.token.decision.Accepted;
import pl.zzpj.subscription_service.domain.token.decision.TokenDecision;
import pl.zzpj.subscription_service.domain.token.reservation.TokenReservationStatus;
import pl.zzpj.subscription_service.persistence.entity.ActiveSubscriptionEntity;
import pl.zzpj.subscription_service.persistence.entity.TokenBalanceEntity;
import pl.zzpj.subscription_service.persistence.entity.TokenReservationEntity;
import pl.zzpj.subscription_service.persistence.repository.ActiveSubscriptionRepository;
import pl.zzpj.subscription_service.persistence.repository.TokenBalanceRepository;
import pl.zzpj.subscription_service.persistence.repository.TokenReservationRepository;

@Service
public class TokenReservationCommandService {

    private final ActiveSubscriptionRepository subscriptionRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenReservationRepository tokenReservationRepository;
    private final TokenReservationPolicy reservationPolicy;
    private final Clock clock;

    private static final String TOKEN_BALANCE_NOT_FOUND_ERROR =
        "Token balance not found for authenticated user ";

    public TokenReservationCommandService(
        ActiveSubscriptionRepository subscriptionRepository,
        TokenBalanceRepository tokenBalanceRepository,
        TokenReservationRepository tokenReservationRepository,
        TokenReservationPolicy reservationPolicy,
        Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tokenBalanceRepository = tokenBalanceRepository;
        this.tokenReservationRepository = tokenReservationRepository;
        this.reservationPolicy = reservationPolicy;
        this.clock = clock;
    }

    @Transactional
    public TokenDecision reserve(
        String userId,
        CreateTokenReservationCommand command
    ) {
        ActiveSubscription subscription = subscriptionRepository
            .findById(userId)
            .map(ActiveSubscriptionEntity::toDomain)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Subscription not found for authenticated user " + userId
                )
            );
        TokenBalance tokenBalance = tokenBalanceRepository
            .findById(userId)
            .map(TokenBalanceEntity::toDomain)
            .orElseThrow(() ->
                new IllegalStateException(
                    TOKEN_BALANCE_NOT_FOUND_ERROR + userId
                )
            );

        Instant now = clock.instant();
        TokenDecision decision = reservationPolicy.decide(
            subscription,
            tokenBalance,
            command.operation(),
            now
        );
        if (decision instanceof Accepted accepted) {
            TokenBalance reservedBalance = tokenBalance.reserve(
                accepted.reservation().tokens()
            );
            tokenBalanceRepository.save(
                TokenBalanceEntity.from(reservedBalance)
            );
            tokenReservationRepository.save(
                TokenReservationEntity.from(
                    accepted.reservation(),
                    now,
                    command.externalOperationId()
                )
            );
        }
        return decision;
    }

    @Transactional
    public TokenReservationEntity consume(String userId, UUID reservationId) {
        TokenReservationEntity reservation = findOwnedReservation(
            userId,
            reservationId
        );
        ensureReserved(reservation);

        TokenBalance tokenBalance = tokenBalanceRepository
            .findById(userId)
            .map(TokenBalanceEntity::toDomain)
            .orElseThrow(() ->
                new IllegalStateException(
                    TOKEN_BALANCE_NOT_FOUND_ERROR + userId
                )
            );

        TokenBalance updatedBalance = tokenBalance.consumeReserved(
            reservation.getTokens()
        );
        tokenBalanceRepository.save(TokenBalanceEntity.from(updatedBalance));
        reservation.markConsumed(clock.instant());
        return tokenReservationRepository.save(reservation);
    }

    @Transactional
    public TokenReservationEntity release(String userId, UUID reservationId) {
        TokenReservationEntity reservation = findOwnedReservation(
            userId,
            reservationId
        );
        ensureReserved(reservation);

        TokenBalance tokenBalance = tokenBalanceRepository
            .findById(userId)
            .map(TokenBalanceEntity::toDomain)
            .orElseThrow(() ->
                new IllegalStateException(
                    TOKEN_BALANCE_NOT_FOUND_ERROR + userId
                )
            );

        TokenBalance updatedBalance = tokenBalance.releaseReserved(
            reservation.getTokens()
        );
        tokenBalanceRepository.save(TokenBalanceEntity.from(updatedBalance));
        reservation.markReleased(clock.instant());
        return tokenReservationRepository.save(reservation);
    }

    private TokenReservationEntity findOwnedReservation(
        String userId,
        UUID reservationId
    ) {
        TokenReservationEntity reservation = tokenReservationRepository
            .findById(reservationId)
            .orElseThrow(() ->
                new IllegalArgumentException("Token reservation not found")
            );
        if (!reservation.getUserId().equals(userId)) {
            throw new IllegalArgumentException(
                "Token reservation belongs to another user"
            );
        }
        return reservation;
    }

    private void ensureReserved(TokenReservationEntity reservation) {
        if (reservation.getStatus() != TokenReservationStatus.RESERVED) {
            throw new IllegalArgumentException(
                "Token reservation is already " + reservation.getStatus()
            );
        }
    }
}
