<script>
    import { operationCost } from "$lib/watermark-console/domain";
    import OperationGate from "./OperationGate.svelte";

    let { processing, result, error, status, onFilesChange, onSubmit } =
        $props();
</script>

<section class="panel">
    <div class="panel-heading">
        <h2>Detect watermark</h2>
        <span class="cost">DETECT: {operationCost("DETECT")} token</span>
    </div>
    <OperationGate operation="DETECT" {status} />
    <label class="field">
        <span>Image</span>
        <input
            type="file"
            accept="image/png,image/jpeg"
            onchange={(event) => onFilesChange(event.currentTarget.files)}
            disabled={Boolean(status)}
        />
    </label>
    <button
        class="btn btn-primary"
        onclick={onSubmit}
        disabled={processing || Boolean(status)}
    >
        {processing ? "Checking..." : "Detect"}
    </button>
    {#if error}<div class="alert alert-error">{error}</div>{/if}
</section>

{#if result}
    <section class="panel result-panel">
        {#if result.watermarked}
            <div class="status yes">Watermark detected</div>
            <div class="details">
                <div>
                    <span>Owner</span><strong>{result.ownerIdentity}</strong>
                </div>
                <div>
                    <span>Payload tier</span><strong
                        >{result.lengthBits ?? "unknown"}</strong
                    >
                </div>
            </div>
        {:else}
            <div class="status no">No watermark detected</div>
        {/if}
    </section>
{/if}
