package pl.zzpj.subscription_service.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.StubFinder;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.web.client.RestClient;

/**
 * Consumer-side contract test.
 *
 * <p>Boots WireMock from the stub jar generated locally by the {@code verifierStubsJar} task
 * (StubsMode.LOCAL) and verifies that a consumer can call {@code /api/subscriptions/status}
 * through the stub and receive the contracted {@code ServiceStatus} response. This proves the
 * provider contract and the consumer's expectations stay in sync without a running provider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(
    ids = "pl.zzpj:subscription-service:+:stubs",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class SubscriptionStatusStubRunnerTest {

    @Autowired
    private StubFinder stubFinder;

    @Test
    void consumerCanReadServiceStatusThroughStub() {
        String baseUrl = stubFinder
            .findStubUrl("pl.zzpj", "subscription-service")
            .toString();

        ServiceStatusResponse response = RestClient.create()
            .get()
            .uri(baseUrl + "/api/subscriptions/status")
            .retrieve()
            .body(ServiceStatusResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.service()).isEqualTo("subscription-service");
        assertThat(response.status()).isEqualTo("UP");
    }

    record ServiceStatusResponse(String service, String status) {
    }
}
