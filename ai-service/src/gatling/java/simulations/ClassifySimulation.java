package simulations;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Gatling load test for {@code POST /api/classify} (issue #19 criterion #3).
 *
 * <p>Posts multipart image uploads to the {@link pl.zzpj.ai_service.ClassificationController}
 * under a ramped injection profile (~10 → 50 → 100 req/s).
 *
 * <p>How to run (requires a running ai-service instance):
 * <pre>
 *   ./gradlew :ai-service:gatlingRun
 * </pre>
 * Override the target and auth token via system properties, e.g.
 * <pre>
 *   ./gradlew :ai-service:gatlingRun \
 *       -DbaseUrl=http://localhost:8080 -Dtoken=&lt;valid-jwt&gt;
 * </pre>
 *
 * <p>Correlate the results with the service's Spring Boot Actuator metrics, e.g.
 * {@code http.server.requests} (latency / throughput / status codes) and JVM metrics
 * exposed at {@code /actuator/metrics} and {@code /actuator/prometheus}.
 */
public class ClassifySimulation extends Simulation {

    private static final String BASE_URL =
            System.getProperty("baseUrl", "http://localhost:8080");
    private static final String TOKEN =
            System.getProperty("token", "replace-with-a-valid-jwt");

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .header("Authorization", "Bearer " + TOKEN)
            .userAgentHeader("gatling-ai-service-loadtest");

    private final ScenarioBuilder classify = scenario("Classify image")
            .exec(
                    http("POST /api/classify")
                            .post("/api/classify")
                            .bodyPart(
                                    io.gatling.javaapi.http.HttpDsl.RawFileBodyPart("file", "test-dog.jpg")
                                            .fileName("test-dog.jpg")
                                            .contentType("image/jpeg")
                            ).asMultipartForm()
                            .check(io.gatling.javaapi.http.HttpDsl.status().in(200, 401))
            );

    {
        setUp(
                classify.injectOpen(
                        // ~10 req/s baseline
                        constantUsersPerSec(10).during(15),
                        // ramp 10 → 50 req/s
                        rampUsersPerSec(10).to(50).during(30),
                        // ramp 50 → 100 req/s peak
                        rampUsersPerSec(50).to(100).during(30),
                        // hold 100 req/s
                        constantUsersPerSec(100).during(30)
                )
        ).protocols(httpProtocol);
    }
}
