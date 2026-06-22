package pl.zzpj.subscription_service.domain.payment;

public sealed interface PaymentOutcome
    permits PaymentOutcome.Succeeded, PaymentOutcome.Failed, PaymentOutcome.Cancelled {

  record Succeeded(String transactionId) implements PaymentOutcome {}

  record Failed(String reason) implements PaymentOutcome {}

  record Cancelled() implements PaymentOutcome {}
}
