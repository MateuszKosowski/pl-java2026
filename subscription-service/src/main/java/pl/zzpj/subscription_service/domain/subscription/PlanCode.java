package pl.zzpj.subscription_service.domain.subscription;

public enum PlanCode {
  FREE,
  STANDARD,
  PRO;

  public boolean canUpgradeTo(PlanCode targetPlan) {
    return targetPlan != null && targetPlan.ordinal() > ordinal();
  }
}
