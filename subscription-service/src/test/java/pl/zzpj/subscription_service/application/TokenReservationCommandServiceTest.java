package pl.zzpj.subscription_service.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.zzpj.subscription_service.application.command.CreateTokenReservationCommand;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.token.TokenBalance;
import pl.zzpj.subscription_service.domain.token.TokenOperation;
import pl.zzpj.subscription_service.domain.token.TokenReservation;
import pl.zzpj.subscription_service.domain.token.TokenReservationPolicy;
import pl.zzpj.subscription_service.domain.token.decision.Accepted;
import pl.zzpj.subscription_service.domain.token.decision.RejectedInsufficientTokens;
import pl.zzpj.subscription_service.domain.token.decision.TokenDecision;
import pl.zzpj.subscription_service.domain.token.reservation.TokenReservationStatus;
import pl.zzpj.subscription_service.persistence.entity.ActiveSubscriptionEntity;
import pl.zzpj.subscription_service.persistence.entity.TokenBalanceEntity;
import pl.zzpj.subscription_service.persistence.entity.TokenReservationEntity;
import pl.zzpj.subscription_service.persistence.repository.ActiveSubscriptionRepository;
import pl.zzpj.subscription_service.persistence.repository.TokenBalanceRepository;
import pl.zzpj.subscription_service.persistence.repository.TokenReservationRepository;

@ExtendWith(MockitoExtension.class)
class TokenReservationCommandServiceTest {

    @Mock
    private ActiveSubscriptionRepository subscriptionRepository;

    @Mock
    private TokenBalanceRepository tokenBalanceRepository;

    @Mock
    private TokenReservationRepository tokenReservationRepository;

    @Mock
    private TokenReservationPolicy reservationPolicy;

    private final Instant fixedInstant = Instant.parse("2026-06-15T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

    private TokenReservationCommandService service;

    @BeforeEach
    void setUp() {
        service = new TokenReservationCommandService(
            subscriptionRepository,
            tokenBalanceRepository,
            tokenReservationRepository,
            reservationPolicy,
            clock
        );
    }

    @Test
    void shouldReserveTokensSuccessfully() {
        String userId = "user123";
        CreateTokenReservationCommand command =
            new CreateTokenReservationCommand(TokenOperation.DETECT, "op1");

        ActiveSubscription sub = new ActiveSubscription(
            userId,
            PlanCode.FREE,
            fixedInstant,
            null
        );
        TokenBalance balance = new TokenBalance(userId, 100, 0);

        when(subscriptionRepository.findById(userId)).thenReturn(
            Optional.of(ActiveSubscriptionEntity.from(sub))
        );
        when(tokenBalanceRepository.findById(userId)).thenReturn(
            Optional.of(TokenBalanceEntity.from(balance))
        );

        TokenReservation reservation = new TokenReservation(
            UUID.randomUUID(),
            userId,
            TokenOperation.DETECT,
            1,
            fixedInstant.plusSeconds(3600)
        );
        TokenDecision decision = new Accepted(reservation);
        when(
            reservationPolicy.decide(
                any(),
                any(),
                eq(TokenOperation.DETECT),
                eq(fixedInstant)
            )
        ).thenReturn(decision);

        TokenDecision result = service.reserve(userId, command);

        assertTrue(result instanceof Accepted);
        verify(tokenBalanceRepository).save(any());
        verify(tokenReservationRepository).save(any());
    }

    @Test
    void shouldNotUpdateRepositoriesWhenReservationRejected() {
        String userId = "user123";
        CreateTokenReservationCommand command =
            new CreateTokenReservationCommand(TokenOperation.DETECT, "op1");

        ActiveSubscription sub = new ActiveSubscription(
            userId,
            PlanCode.FREE,
            fixedInstant,
            null
        );
        TokenBalance balance = new TokenBalance(userId, 0, 0);

        when(subscriptionRepository.findById(userId)).thenReturn(
            Optional.of(ActiveSubscriptionEntity.from(sub))
        );
        when(tokenBalanceRepository.findById(userId)).thenReturn(
            Optional.of(TokenBalanceEntity.from(balance))
        );

        TokenDecision decision = new RejectedInsufficientTokens(
            TokenOperation.DETECT,
            1,
            0
        );
        when(
            reservationPolicy.decide(
                any(),
                any(),
                eq(TokenOperation.DETECT),
                eq(fixedInstant)
            )
        ).thenReturn(decision);

        TokenDecision result = service.reserve(userId, command);

        assertTrue(result instanceof RejectedInsufficientTokens);
        verify(tokenBalanceRepository, never()).save(any());
        verify(tokenReservationRepository, never()).save(any());
    }

    @Test
    void shouldConsumeReservationSuccessfully() {
        String userId = "user123";
        UUID resId = UUID.randomUUID();
        TokenReservation reservation = new TokenReservation(
            resId,
            userId,
            TokenOperation.DETECT,
            5,
            fixedInstant.plusSeconds(3600)
        );
        TokenReservationEntity entity = TokenReservationEntity.from(
            reservation,
            fixedInstant,
            "extOp1"
        );

        when(tokenReservationRepository.findById(resId)).thenReturn(
            Optional.of(entity)
        );
        when(tokenBalanceRepository.findById(userId)).thenReturn(
            Optional.of(
                TokenBalanceEntity.from(new TokenBalance(userId, 100, 5))
            )
        );

        service.consume(userId, resId);

        assertEquals(TokenReservationStatus.CONSUMED, entity.getStatus());
        verify(tokenBalanceRepository).save(any());
        verify(tokenReservationRepository).save(any());
    }

    @Test
    void shouldReleaseReservationSuccessfully() {
        String userId = "user123";
        UUID resId = UUID.randomUUID();
        TokenReservation reservation = new TokenReservation(
            resId,
            userId,
            TokenOperation.DETECT,
            5,
            fixedInstant.plusSeconds(3600)
        );
        TokenReservationEntity entity = TokenReservationEntity.from(
            reservation,
            fixedInstant,
            "extOp1"
        );

        when(tokenReservationRepository.findById(resId)).thenReturn(
            Optional.of(entity)
        );
        when(tokenBalanceRepository.findById(userId)).thenReturn(
            Optional.of(
                TokenBalanceEntity.from(new TokenBalance(userId, 100, 5))
            )
        );

        service.release(userId, resId);

        assertEquals(TokenReservationStatus.RELEASED, entity.getStatus());
        verify(tokenBalanceRepository).save(any());
        verify(tokenReservationRepository).save(any());
    }

    @Test
    void shouldThrowExceptionWhenConsumingNonOwnedReservation() {
        String userId = "user123";
        UUID resId = UUID.randomUUID();
        TokenReservation reservation = new TokenReservation(
            resId,
            "otherUser",
            TokenOperation.DETECT,
            5,
            fixedInstant.plusSeconds(3600)
        );
        TokenReservationEntity entity = TokenReservationEntity.from(
            reservation,
            fixedInstant,
            "extOp1"
        );

        when(tokenReservationRepository.findById(resId)).thenReturn(
            Optional.of(entity)
        );

        assertThrows(IllegalArgumentException.class, () ->
            service.consume(userId, resId)
        );
    }

    @Test
    void shouldThrowExceptionWhenConsumingAlreadyConsumed() {
        String userId = "user123";
        UUID resId = UUID.randomUUID();
        TokenReservation reservation = new TokenReservation(
            resId,
            userId,
            TokenOperation.DETECT,
            5,
            fixedInstant.plusSeconds(3600)
        );
        TokenReservationEntity entity = TokenReservationEntity.from(
            reservation,
            fixedInstant,
            "extOp1"
        );
        entity.markConsumed(fixedInstant);

        when(tokenReservationRepository.findById(resId)).thenReturn(
            Optional.of(entity)
        );

        assertThrows(IllegalArgumentException.class, () ->
            service.consume(userId, resId)
        );
    }
}
