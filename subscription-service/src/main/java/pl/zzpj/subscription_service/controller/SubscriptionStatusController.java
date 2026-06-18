package pl.zzpj.subscription_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.zzpj.subscription_service.controller.dto.ServiceStatus;

@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscription Status", description = "Service health and status API")
public class SubscriptionStatusController {

  @GetMapping("/status")
  @Operation(
      summary = "Service status",
      description = "Returns the current status of the subscription service.")
  public ServiceStatus status() {
    return new ServiceStatus("subscription-service", "UP");
  }
}
