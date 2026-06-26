<script>
    import { formatPlanExpiry } from "$lib/watermark-console/domain";

    let {
        accountLoading,
        accountError,
        subscription,
        tokenBalance,
        currentRole,
        onRefresh,
    } = $props();

    function availableTokens() {
        return tokenBalance?.availableTokens ?? 0;
    }

    function reservedTokens() {
        return tokenBalance?.reservedTokens ?? 0;
    }
</script>

<section class="account-panel">
    {#if accountLoading}
        <div class="metric">Loading subscription data...</div>
    {:else if accountError}
        <div class="alert alert-error">{accountError}</div>
    {:else}
        <div class="metric">
            <span>Plan</span>
            <strong>{subscription?.planCode ?? "UNKNOWN"}</strong>
        </div>
        <div class="metric">
            <span>Available tokens</span>
            <strong>{availableTokens()}</strong>
        </div>
        <div class="metric">
            <span>Reserved tokens</span>
            <strong>{reservedTokens()}</strong>
        </div>
        <div class="metric">
            <span>Role</span>
            <strong>{currentRole}</strong>
        </div>
        <div class="metric">
            <span>Plan valid until</span>
            <strong
                class:metric-date={Boolean(subscription?.activeUntil)}
                title={subscription?.activeUntil ?? undefined}
                >{formatPlanExpiry(subscription?.activeUntil)}</strong
            >
        </div>
        <button class="btn btn-outline compact" onclick={onRefresh}
            >Refresh</button
        >
    {/if}
</section>
