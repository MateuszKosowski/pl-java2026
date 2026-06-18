package pl.zzpj.subscription_service.controller.dto.payment;

import pl.zzpj.subscription_service.domain.subscription.PlanCode;

public record CreatePaymentSessionRequest(PlanCode targetPlan) {}
