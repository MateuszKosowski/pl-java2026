import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("""
        Given the subscription service is running
        When a client requests GET /api/subscriptions/status
        Then it responds 200 with the service identity and UP status
    """)
    request {
        method GET()
        url "/api/subscriptions/status"
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
                service: "subscription-service",
                status: "UP"
        )
        bodyMatchers {
            jsonPath('$.service', byEquality())
            jsonPath('$.status', byEquality())
        }
    }
}
