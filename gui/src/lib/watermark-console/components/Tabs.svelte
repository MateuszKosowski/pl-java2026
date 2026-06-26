<script>
    let { tabs, activeTab, tabStatus, onSelect } = $props();
</script>

<nav class="tabs">
    {#each tabs as tab}
        {@const status = tabStatus(tab.id)}
        {@const disabled = Boolean(status)}
        <button
            class="tab"
            class:active={activeTab === tab.id}
            class:disabled
            onclick={() => onSelect(tab.id)}
            {disabled}
            title={disabled ? status : tab.label}
        >
            {tab.label}
        </button>
    {/each}
</nav>
