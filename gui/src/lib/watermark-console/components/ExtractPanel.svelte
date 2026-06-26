<script>
    import { operationCost } from "$lib/watermark-console/domain";
    import OperationGate from "./OperationGate.svelte";

    let {
        processing,
        result,
        notice,
        error,
        status,
        onFilesChange,
        onSubmit,
    } = $props();
</script>

<section class="panel">
    <div class="panel-heading">
        <h2>Extract hidden text</h2>
        <span class="cost">EXTRACT: {operationCost("EXTRACT")} tokens</span>
    </div>
    <OperationGate operation="EXTRACT" {status} />
    <label class="field">
        <span>Watermarked image</span>
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
        {processing ? "Reading..." : "Extract"}
    </button>
    {#if notice}<div class="alert alert-info">{notice}</div>{/if}
    {#if error}<div class="alert alert-error">{error}</div>{/if}
</section>

{#if result}
    <section class="panel result-panel">
        <h2>Hidden text</h2>
        <div class="details">
            <div>
                <span>Owner</span><strong>{result.ownerIdentity}</strong>
            </div>
        </div>
        <pre>{result.text}</pre>
    </section>
{/if}
