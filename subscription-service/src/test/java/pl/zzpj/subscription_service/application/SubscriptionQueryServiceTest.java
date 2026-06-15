package pl.zzpj.subscription_service.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.zzpj.subscription_service.domain.subscription.ActiveSubscription;
import pl.zzpj.subscription_service.domain.subscription.PlanCode;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionCatalog;
import pl.zzpj.subscription_service.domain.subscription.SubscriptionPlan;
import pl.zzpj.subscription_service.domain.token.TokenBalance;
import pl.zzpj.subscription_service.persistence.SubscriptionStore;

@ExtendWith(MockitoExtension.class)
class SubscriptionQueryServiceTest {

    @Mock
    private SubscriptionCatalog subscriptionCatalog;

    @Mock
    private SubscriptionStore subscriptionStore;

    private final Instant fixedInstant = Instant.parse("2026-06-15T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

    private SubscriptionQueryService subscriptionQueryService;

    @BeforeEach
    void setUp() {
        subscriptionQueryService = new SubscriptionQueryService(
            subscriptionCatalog,
            subscriptionStore,
            clock
        );
    }

    @Test
    void shouldReturnAvailablePlansSortedByOrdinal() {
        SubscriptionPlan free = new SubscriptionPlan(
            PlanCode.FREE,
            50,
            Set.of()
        );
        SubscriptionPlan pro = new SubscriptionPlan(
            PlanCode.PRO,
            2500,
            Set.of()
        );

        when(subscriptionCatalog.plans()).thenReturn(
            Map.of(PlanCode.FREE, free, PlanCode.PRO, pro)
        );

        var plans = subscriptionQueryService.availablePlans();

        assertEquals(2, plans.size());
        assertEquals(PlanCode.FREE, plans.get(0).code());
        assertEquals(PlanCode.PRO, plans.get(1).code());
    }

    @Test
    void shouldReturnStateForUser() {
        String userId = "user123";
        SubscriptionPlan free = new SubscriptionPlan(
            PlanCode.FREE,
            50,
            Set.of()
        );
        when(subscriptionCatalog.findPlan(PlanCode.FREE)).thenReturn(
            Optional.of(free)
        );

        UserSubscriptionState expectedState = new UserSubscriptionState(
            new ActiveSubscription(userId, PlanCode.FREE, fixedInstant, null),
            new TokenBalance(userId, 50, 0)
        );
        when(subscriptionStore.getOrCreate(eq(userId), any())).thenReturn(
            expectedState
        );

        UserSubscriptionState actualState = subscriptionQueryService.stateFor(
            userId
        );

        assertEquals(expectedState, actualState);
    }
}
