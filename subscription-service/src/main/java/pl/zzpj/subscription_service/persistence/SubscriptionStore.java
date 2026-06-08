package pl.zzpj.subscription_service.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.zzpj.subscription_service.application.UserSubscriptionState;
import pl.zzpj.subscription_service.persistence.entity.ActiveSubscriptionEntity;
import pl.zzpj.subscription_service.persistence.entity.TokenBalanceEntity;
import pl.zzpj.subscription_service.persistence.repository.ActiveSubscriptionRepository;
import pl.zzpj.subscription_service.persistence.repository.TokenBalanceRepository;

import java.util.Optional;

@Component
public class SubscriptionStore {

    private final ActiveSubscriptionRepository subscriptionRepository;
    private final TokenBalanceRepository tokenBalanceRepository;

    public SubscriptionStore(
            ActiveSubscriptionRepository subscriptionRepository,
            TokenBalanceRepository tokenBalanceRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tokenBalanceRepository = tokenBalanceRepository;
    }

    @Transactional
    public UserSubscriptionState getOrCreate(String userId, UserSubscriptionState initialState) {
        return find(userId).orElseGet(() -> create(initialState));
    }

    @Transactional
    public UserSubscriptionState save(UserSubscriptionState state) {
        subscriptionRepository.save(ActiveSubscriptionEntity.from(state.subscription()));
        tokenBalanceRepository.save(TokenBalanceEntity.from(state.tokenBalance()));
        return state;
    }

    @Transactional(readOnly = true)
    public Optional<UserSubscriptionState> find(String userId) {
        return subscriptionRepository.findById(userId)
                .flatMap(subscription -> tokenBalanceRepository.findById(userId)
                        .map(tokenBalance -> new UserSubscriptionState(
                                subscription.toDomain(),
                                tokenBalance.toDomain()
                        )));
    }

    private UserSubscriptionState create(UserSubscriptionState initialState) {
        subscriptionRepository.insertIfMissing(
                initialState.subscription().userId(),
                initialState.subscription().planCode().name(),
                initialState.subscription().activeFrom()
        );
        tokenBalanceRepository.insertIfMissing(
                initialState.tokenBalance().userId(),
                initialState.tokenBalance().availableTokens(),
                initialState.tokenBalance().reservedTokens()
        );
        return find(initialState.subscription().userId())
                .orElseThrow(() -> new IllegalStateException("Subscription state could not be created"));
    }
}
