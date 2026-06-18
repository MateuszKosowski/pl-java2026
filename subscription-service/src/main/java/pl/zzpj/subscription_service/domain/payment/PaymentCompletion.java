package pl.zzpj.subscription_service.domain.payment;

public record PaymentCompletion(PaymentSession session, boolean completedNow) {}
