package pl.zzpj.subscription_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionStatusController {

    @GetMapping("/status")
    public ServiceStatus status() {
        return new ServiceStatus("subscription-service", "UP");
    }

    public record ServiceStatus(String service, String status) {
    }
}
