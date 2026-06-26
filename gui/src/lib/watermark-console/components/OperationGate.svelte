<script>
    import { operationName } from "$lib/watermark-console/domain";

    let { operation, status } = $props();
</script>

{#if status}
    <div class="alert alert-warning">{status}</div>
{:else}
    <div class="notice">
        {operationName(operation)} is available for your plan.
    </div>
{/if}
