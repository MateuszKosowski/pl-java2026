package pl.zzpj.subscription_service.controller;

import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import pl.zzpj.subscription_service.application.PaymentApplicationService;
import pl.zzpj.subscription_service.application.UserIdentityResolver;
import pl.zzpj.subscription_service.controller.dto.payment.CreatePaymentSessionRequest;
import pl.zzpj.subscription_service.controller.dto.payment.PaymentSessionResponse;
import pl.zzpj.subscription_service.domain.payment.PaymentOutcome;
import pl.zzpj.subscription_service.domain.payment.PaymentSession;

@RestController
@RequestMapping("/api/payments/mock/sessions")
public class MockPaymentController {

  private final PaymentApplicationService paymentService;
  private final UserIdentityResolver userIdentityResolver;

  public MockPaymentController(
      PaymentApplicationService paymentService, UserIdentityResolver userIdentityResolver) {
    this.paymentService = paymentService;
    this.userIdentityResolver = userIdentityResolver;
  }

  @PostMapping
  public PaymentSessionResponse createSession(
      @RequestBody CreatePaymentSessionRequest request, Principal principal) {
    String userId = userIdentityResolver.resolve(principal);
    PaymentSession session = paymentService.initiatePayment(userId, request.targetPlan());
    return PaymentSessionResponse.from(session);
  }

  @PostMapping("/{sessionId}/succeed")
  public PaymentSessionResponse succeed(@PathVariable UUID sessionId, Principal principal) {
    String userId = userIdentityResolver.resolve(principal);
    PaymentSession session =
        paymentService.completePayment(
            userId, sessionId, new PaymentOutcome.Succeeded(UUID.randomUUID().toString()));
    return PaymentSessionResponse.from(session);
  }

  @PostMapping("/{sessionId}/fail")
  public PaymentSessionResponse fail(@PathVariable UUID sessionId, Principal principal) {
    String userId = userIdentityResolver.resolve(principal);
    PaymentSession session =
        paymentService.completePayment(
            userId, sessionId, new PaymentOutcome.Failed("Mocked failure"));
    return PaymentSessionResponse.from(session);
  }

  @PostMapping("/{sessionId}/cancel")
  public PaymentSessionResponse cancel(@PathVariable UUID sessionId, Principal principal) {
    String userId = userIdentityResolver.resolve(principal);
    PaymentSession session =
        paymentService.completePayment(userId, sessionId, new PaymentOutcome.Cancelled());
    return PaymentSessionResponse.from(session);
  }
}
