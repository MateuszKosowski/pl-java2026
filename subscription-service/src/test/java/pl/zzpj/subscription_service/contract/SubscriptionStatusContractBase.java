package pl.zzpj.subscription_service.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import pl.zzpj.subscription_service.controller.SubscriptionStatusController;

/**
 * Base class for the Spring Cloud Contract generated provider tests.
 *
 * <p>Sets up {@link RestAssuredMockMvc} in standalone mode against the real
 * {@link SubscriptionStatusController}. The {@code /api/subscriptions/status} endpoint is
 * whitelisted (permitAll) and DB-free, so no full Spring context, security filter chain or
 * database is required for the generated tests to pass.
 */
public abstract class SubscriptionStatusContractBase {

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.standaloneSetup(new SubscriptionStatusController());
    }
}
