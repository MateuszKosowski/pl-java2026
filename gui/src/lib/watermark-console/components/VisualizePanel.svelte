<script>
    import { operationCost } from "$lib/watermark-console/domain";
    import OperationGate from "./OperationGate.svelte";

    let { processing, imageUrl, error, status, onFilesChange, onSubmit } =
        $props();
</script>

<section class="panel">
    <div class="panel-heading">
        <h2>Visualize watermark footprint</h2>
        <span class="cost">VISUALIZE: {operationCost("VISUALIZE")} tokens</span>
    </div>
    <OperationGate operation="VISUALIZE" {status} />
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
        {processing ? "Rendering..." : "Visualize"}
    </button>
    {#if error}<div class="alert alert-error">{error}</div>{/if}
</section>

{#if imageUrl}
    <section class="panel result-panel">
        <h2>Visualization</h2>
        <div class="image-frame">
            <img src={imageUrl} alt="Watermark visualization" />
        </div>
        <a
            href={imageUrl}
            download="watermark_visualization.png"
            class="download-link"
        >
            <button class="btn btn-success">Download visualization</button>
        </a>
    </section>
{/if}
