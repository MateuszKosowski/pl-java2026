<script>
    import { operationName } from "$lib/watermark-console/domain";

    let {
        plans,
        subscription,
        paymentSession,
        paymentProcessing,
        paymentError,
        canUpgradeTo,
        onInitiatePayment,
        onCompletePayment,
        onDismissPayment,
    } = $props();
</script>

<section class="panel">
    <h2>Subscription plans</h2>
    <div class="plans-grid">
        {#each plans as plan}
            <div
                class="plan-card"
                class:current={subscription?.planCode === plan.code}
            >
                <h3>{plan.code}</h3>
                <p class="plan-tokens">
                    <strong>{plan.monthlyTokens}</strong> tokens / month
                </p>
                <ul class="plan-ops">
                    {#each plan.allowedOperations as op}
                        <li>{operationName(op)}</li>
                    {/each}
                </ul>
                {#if subscription?.planCode === plan.code}
                    <button class="btn btn-outline" disabled
                        >Current plan</button
                    >
                {:else if canUpgradeTo(plan.code)}
                    <button
                        class="btn btn-primary"
                        onclick={() => onInitiatePayment(plan.code)}
                        disabled={paymentProcessing}
                    >
                        Upgrade to {plan.code}
                    </button>
                {:else}
                    <button class="btn btn-outline" disabled
                        >Downgrade unavailable</button
                    >
                {/if}
            </div>
        {/each}
    </div>

    {#if paymentSession}
        <div
            class="payment-session-status"
            class:pending={paymentSession.status === "PENDING"}
        >
            <div class="panel">
                <h3>Mock Payment Session</h3>
                <p>
                    Target Plan: <strong>{paymentSession.targetPlan}</strong>
                </p>
                <p>
                    Status: <strong
                        class="status-tag"
                        class:status-pending={paymentSession.status ===
                            "PENDING"}>{paymentSession.status}</strong
                    >
                </p>

                {#if paymentSession.status === "PENDING"}
                    <div class="payment-actions">
                        <button
                            class="btn btn-success"
                            onclick={() => onCompletePayment("succeed")}
                            disabled={paymentProcessing}
                        >
                            {paymentProcessing
                                ? "Processing..."
                                : "Simulate Success"}
                        </button>
                        <button
                            class="btn btn-error"
                            onclick={() => onCompletePayment("fail")}
                            disabled={paymentProcessing}
                        >
                            Simulate Failure
                        </button>
                        <button
                            class="btn btn-outline"
                            onclick={() => onCompletePayment("cancel")}
                            disabled={paymentProcessing}
                        >
                            Cancel
                        </button>
                    </div>
                {:else}
                    <div class="notice">
                        Session finished. You can now close this or start a new
                        upgrade.
                        <button
                            class="btn btn-outline compact"
                            onclick={onDismissPayment}>Dismiss</button
                        >
                    </div>
                {/if}
            </div>
        </div>
    {/if}

    {#if paymentError}
        <div class="alert alert-error">{paymentError}</div>
    {/if}
</section>
