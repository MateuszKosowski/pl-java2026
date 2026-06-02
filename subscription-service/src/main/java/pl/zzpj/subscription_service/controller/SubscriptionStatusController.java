package pl.zzpj.subscription_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.zzpj.subscription_service.controller.dto.ServiceStatus;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionStatusController {

    @GetMapping("/status")
    public ServiceStatus status() {
        return new ServiceStatus("subscription-service", "UP");
    }
}
