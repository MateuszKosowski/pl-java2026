<script>
    import { onMount } from 'svelte';

    let token = $state('');
    let activeTab = $state('embed');
    let accountLoading = $state(true);
    let accountError = $state('');
    let subscription = $state(null);
    let tokenBalance = $state(null);
    let plans = $state([]);
    let currentRole = $state('USER');

    const tabs = [
        { id: 'embed', label: 'Embed' },
        { id: 'detect', label: 'Detect' },
        { id: 'extract', label: 'Extract' },
        { id: 'visualize', label: 'Visualize' }
    ];

    const operationCosts = {
        CAPACITY_CHECK: 0,
        DETECT: 1,
        EXTRACT: 2,
        VISUALIZE: 3,
        EMBED_768: 5,
        EMBED_1024: 8,
        AI_CLASSIFICATION: 2
    };

    const operationLabels = {
        CAPACITY_CHECK: 'Capacity check',
        DETECT: 'Detect',
        EXTRACT: 'Extract',
        VISUALIZE: 'Visualize',
        EMBED_768: 'Embed basic',
        EMBED_1024: 'Embed large',
        AI_CLASSIFICATION: 'AI classification'
    };

    let embedFiles = $state();
    let watermarkText = $state('');
    let embedProcessing = $state(false);
    let resultImageUrl = $state(null);
    let classification = $state(null);
    let embedError = $state('');
    let capacity = $state(null);
    let capacityChecking = $state(false);
    let capacityError = $state(null);
    let capacityAbort = null;
    let lastProbedFileSignature = '';

    let detectFiles = $state();
    let detectProcessing = $state(false);
    let detectResult = $state(null);
    let detectError = $state('');

    let extractFiles = $state();
    let extractProcessing = $state(false);
    let extractResult = $state(null);
    let extractNotice = $state('');
    let extractError = $state('');

    let visualizeFiles = $state();
    let visualizeProcessing = $state(false);
    let visualizeImageUrl = $state(null);
    let visualizeError = $state('');

    const textEncoder = new TextEncoder();

    onMount(async () => {
        token = localStorage.getItem('jwt_token') ?? '';
        if (!token) {
            window.location.href = '/login';
            return;
        }
        currentRole = readRole(token);
        await loadAccount();
    });

    function logout() {
        localStorage.removeItem('jwt_token');
        window.location.href = '/login';
    }

    function readRole(jwt) {
        try {
            const parts = jwt.split('.');
            if (parts.length < 2) return 'USER';
            const segment = parts[1].replace(/-/g, '+').replace(/_/g, '/');
            const padded = segment + '='.repeat((4 - segment.length % 4) % 4);
            const payload = JSON.parse(atob(padded));
            if (payload.role) return String(payload.role).toUpperCase();
            if (payload.sub === 'admin' && payload.userId === 1) return 'ADMIN';
        } catch {
            return 'USER';
        }
        return 'USER';
    }

    async function loadAccount() {
        accountLoading = true;
        accountError = '';
        try {
            plans = await apiGet('/api/subscriptions/plans');
            subscription = await apiGet('/api/subscriptions/me');
            tokenBalance = await apiGet('/api/subscriptions/me/tokens');
        } catch (error) {
            accountError = error instanceof Error ? error.message : 'Nie udało się pobrać danych subskrypcji.';
        } finally {
            accountLoading = false;
        }
    }

    async function refreshTokens() {
        try {
            tokenBalance = await apiGet('/api/subscriptions/me/tokens');
        } catch {
            accountError = 'Nie udało się odświeżyć salda tokenów.';
        }
    }

    async function apiGet(url) {
        const response = await fetch(url, {
            headers: { Authorization: `Bearer ${token}` }
        });
        if (response.status === 401) {
            logout();
            throw new Error('Sesja wygasła.');
        }
        if (!response.ok) throw new Error(await readError(response));
        return response.json();
    }

    function currentPlan() {
        if (!subscription) return null;
        return plans.find((plan) => plan.code === subscription.planCode) ?? null;
    }

    function planAllows(operation) {
        const plan = currentPlan();
        return Boolean(plan?.allowedOperations?.includes(operation));
    }

    function operationCost(operation) {
        return operationCosts[operation] ?? 0;
    }

    function availableTokens() {
        return tokenBalance?.availableTokens ?? 0;
    }

    function reservedTokens() {
        return tokenBalance?.reservedTokens ?? 0;
    }

    function hasTokensFor(operation) {
        return availableTokens() >= operationCost(operation);
    }

    function operationName(operation) {
        return operationLabels[operation] ?? operation;
    }

    function embedOperation() {
        if (!capacity?.imageOk || !capacity.lengthBits) return null;
        return `EMBED_${capacity.lengthBits}`;
    }

    function embedActionStatus() {
        const operation = embedOperation();
        return operation ? actionStatus(operation) : '';
    }

    function embedInputDisabled() {
        return Boolean(embedActionStatus());
    }

    function embedAiStatus() {
        const operation = embedOperation();
        if (!operation || !tokenBalance) return '';
        const embedCost = operationCost(operation);
        const aiCost = operationCost('AI_CLASSIFICATION');
        if (!planAllows('AI_CLASSIFICATION')) return 'AI classification will be skipped by this plan.';
        if (availableTokens() - embedCost >= aiCost) return `AI classification available: +${aiCost} tokens.`;
        return 'AI classification will be skipped because the remaining token balance is too low.';
    }

    function actionStatus(operation) {
        if (!subscription || !tokenBalance) return 'Ładowanie danych planu.';
        if (!planAllows(operation)) return `Plan ${subscription.planCode} does not allow ${operationName(operation)}.`;
        if (!hasTokensFor(operation)) return `Not enough tokens: requires ${operationCost(operation)}, available ${availableTokens()}.`;
        return '';
    }

    function tabOperation(tabId) {
        if (tabId === 'detect') return 'DETECT';
        if (tabId === 'extract') return 'EXTRACT';
        if (tabId === 'visualize') return 'VISUALIZE';
        return null;
    }

    function tabStatus(tabId) {
        const operation = tabOperation(tabId);
        return operation ? actionStatus(operation) : '';
    }

    function tabDisabled(tabId) {
        return Boolean(tabStatus(tabId));
    }

    function canUseOperation(operation) {
        return !actionStatus(operation);
    }

    function selectTab(tabId) {
        if (!tabDisabled(tabId)) activeTab = tabId;
    }

    function validateImage(files) {
        if (!files || files.length === 0) return 'Wybierz obraz z dysku.';
        if (files[0].size < 100) return 'Wybrany plik jest zbyt mały lub uszkodzony.';
        return '';
    }

    function postImage(url, image, text) {
        const formData = new FormData();
        formData.append('image', image);
        if (text !== undefined) formData.append('text', text);
        return fetch(url, {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
            body: formData
        });
    }

    async function readError(response) {
        try {
            const data = await response.json();
            if (typeof data.detail === 'string') return data.detail;
            if (data.detail?.message) return data.detail.message;
            if (data.message) return data.message;
            if (data.error) return data.error;
            if (data.code) return data.code;
        } catch {
            // fall through
        }
        return 'Wystąpił błąd podczas przetwarzania.';
    }

    function utf8ByteLength(text) {
        return textEncoder.encode(text).length;
    }

    function fileSignature(file) {
        return `${file.name}|${file.size}|${file.lastModified}`;
    }

    async function probeCapacity() {
        if (!embedFiles || embedFiles.length === 0) return;
        capacityAbort?.abort();
        const controller = new AbortController();
        capacityAbort = controller;
        capacity = null;
        capacityError = null;
        capacityChecking = true;
        try {
            const formData = new FormData();
            formData.append('image', embedFiles[0]);
            const response = await fetch('/api/watermark/capacity', {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}` },
                body: formData,
                signal: controller.signal
            });
            if (controller.signal.aborted) return;
            if (response.ok) {
                capacity = await response.json();
            } else {
                capacityError = await readError(response);
            }
        } catch (error) {
            if (error instanceof DOMException && error.name === 'AbortError') return;
            capacityError = 'Nie udało się sprawdzić pojemności obrazu.';
        } finally {
            if (capacityAbort === controller) {
                capacityAbort = null;
                capacityChecking = false;
            }
        }
    }

    $effect(() => {
        if (embedFiles && embedFiles.length > 0) {
            const sig = fileSignature(embedFiles[0]);
            if (sig !== lastProbedFileSignature) {
                lastProbedFileSignature = sig;
                probeCapacity();
            }
        } else {
            lastProbedFileSignature = '';
            capacity = null;
            capacityError = null;
        }
    });

    $effect(() => {
        if (tabDisabled(activeTab)) activeTab = 'embed';
    });

    async function embedWatermark() {
        const validation = validateImage(embedFiles);
        if (validation) { embedError = validation; return; }
        if (capacityChecking) { embedError = 'Poczekaj na sprawdzenie pojemności obrazu.'; return; }
        if (!capacity?.imageOk) { embedError = 'Obraz nie spełnia minimalnych wymagań watermarkingu.'; return; }
        const operation = embedOperation();
        if (!operation) { embedError = 'Nie udało się ustalić typu operacji.'; return; }
        const status = actionStatus(operation);
        if (status) { embedError = status; return; }
        if (!watermarkText.trim()) { embedError = 'Wpisz tekst do ukrycia.'; return; }
        if (utf8ByteLength(watermarkText) > capacity.maxTextBytes) {
            embedError = `Tekst jest za długi. Limit dla tego obrazu to ${capacity.maxTextBytes} bajtów.`;
            return;
        }

        embedError = '';
        embedProcessing = true;
        resultImageUrl = null;
        classification = null;

        try {
            const response = await postImage('/api/watermark/embed', embedFiles[0], watermarkText);
            if (response.ok) {
                classification = readClassification(response.headers);
                resultImageUrl = URL.createObjectURL(await response.blob());
                await refreshTokens();
            } else {
                embedError = await readError(response);
                await refreshTokens();
            }
        } catch {
            embedError = 'Błąd połączenia z serwerem.';
        } finally {
            embedProcessing = false;
        }
    }

    async function detectWatermark() {
        const validation = validateImage(detectFiles);
        if (validation) { detectError = validation; return; }
        const status = actionStatus('DETECT');
        if (status) { detectError = status; return; }

        detectError = '';
        detectProcessing = true;
        detectResult = null;
        try {
            const response = await postImage('/api/watermark/detect', detectFiles[0]);
            if (response.ok) {
                detectResult = await response.json();
            } else {
                detectError = await readError(response);
            }
            await refreshTokens();
        } catch {
            detectError = 'Błąd połączenia z serwerem.';
        } finally {
            detectProcessing = false;
        }
    }

    async function extractWatermark() {
        const validation = validateImage(extractFiles);
        if (validation) { extractError = validation; return; }
        const status = actionStatus('EXTRACT');
        if (status) { extractError = status; return; }

        extractError = '';
        extractNotice = '';
        extractProcessing = true;
        extractResult = null;
        try {
            const response = await postImage('/api/watermark/extract', extractFiles[0]);
            if (response.ok) {
                extractResult = await response.json();
            } else if (response.status === 400) {
                extractNotice = await readError(response);
            } else if (response.status === 403) {
                extractError = currentRole === 'ADMIN'
                    ? 'Admin should be allowed to read this watermark. Log in again to refresh the role claim.'
                    : 'Nie jesteś właścicielem tego znaku wodnego.';
            } else {
                extractError = await readError(response);
            }
            await refreshTokens();
        } catch {
            extractError = 'Błąd połączenia z serwerem.';
        } finally {
            extractProcessing = false;
        }
    }

    async function visualizeWatermark() {
        const validation = validateImage(visualizeFiles);
        if (validation) { visualizeError = validation; return; }
        const status = actionStatus('VISUALIZE');
        if (status) { visualizeError = status; return; }

        visualizeError = '';
        visualizeProcessing = true;
        visualizeImageUrl = null;
        try {
            const response = await postImage('/api/watermark/visualize', visualizeFiles[0]);
            if (response.ok) {
                visualizeImageUrl = URL.createObjectURL(await response.blob());
            } else {
                visualizeError = await readError(response);
            }
            await refreshTokens();
        } catch {
            visualizeError = 'Błąd połączenia z serwerem.';
        } finally {
            visualizeProcessing = false;
        }
    }

    function readClassification(headers) {
        const category = headers.get('X-Image-Category');
        const label = headers.get('X-Image-Label');
        const confidence = headers.get('X-Image-Confidence');
        const categoryConfidence = headers.get('X-Image-Category-Confidence');

        if (!category || category === 'unknown') return null;
        const toPercent = (value) => (value ? Math.round(parseFloat(value) * 100) : null);
        return {
            category,
            label: label && label !== 'unknown' ? label : null,
            confidence: toPercent(confidence),
            categoryConfidence: toPercent(categoryConfidence)
        };
    }
</script>

<main class="container">
    <header class="topbar">
        <div>
            <h1>Watermark Console</h1>
            <p>PNG watermarking with subscription-aware token limits.</p>
        </div>
        <button class="btn btn-outline compact" onclick={logout}>Logout</button>
    </header>

    <section class="account-panel">
        {#if accountLoading}
            <div class="metric">Loading subscription data...</div>
        {:else if accountError}
            <div class="alert alert-error">{accountError}</div>
        {:else}
            <div class="metric">
                <span>Plan</span>
                <strong>{subscription?.planCode ?? 'UNKNOWN'}</strong>
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
            <button class="btn btn-outline compact" onclick={loadAccount}>Refresh</button>
        {/if}
    </section>

    <nav class="tabs">
        {#each tabs as tab}
            {@const disabled = tabDisabled(tab.id)}
            <button
                class="tab"
                class:active={activeTab === tab.id}
                class:disabled={disabled}
                onclick={() => selectTab(tab.id)}
                disabled={disabled}
                title={disabled ? tabStatus(tab.id) : tab.label}
            >
                {tab.label}
            </button>
        {/each}
    </nav>

    {#if activeTab === 'embed'}
        <section class="panel">
            <div class="panel-heading">
                <h2>Embed watermark</h2>
                <span class="cost">{embedOperation() ? `${operationName(embedOperation())}: ${operationCost(embedOperation())} tokens` : 'Capacity check is free'}</span>
            </div>

            <label class="field">
                <span>Base image</span>
                <input type="file" accept="image/png" bind:files={embedFiles} />
            </label>

            {#if capacityChecking}
                <div class="notice">Checking image capacity...</div>
            {:else if capacityError}
                <div class="alert alert-error">{capacityError}</div>
            {:else if capacity && !capacity.imageOk}
                <div class="alert alert-error">
                    Image is too small: {capacity.imageWidth}x{capacity.imageHeight}. Minimum is {capacity.minImageWidth}x{capacity.minImageHeight}.
                </div>
            {:else if capacity}
                {@const embedStatus = embedActionStatus()}
                <div class="capacity-grid">
                    <div><span>Size</span><strong>{capacity.imageWidth}x{capacity.imageHeight}</strong></div>
                    <div><span>Tier</span><strong>{embedOperation()}</strong></div>
                    <div><span>Text limit</span><strong>{capacity.maxTextBytes} B</strong></div>
                    <div class:blocked={embedStatus}><span>Plan check</span><strong>{embedStatus || 'Allowed'}</strong></div>
                </div>
                {#if embedStatus}
                    <div class="alert alert-warning">
                        This image requires {operationName(embedOperation())}, which is not available for the current plan or token balance. The embed action is disabled for this image.
                    </div>
                {/if}
                {#if embedAiStatus()}
                    <div class="notice">{embedAiStatus()}</div>
                {/if}
            {/if}

            <label class="field">
                <span>
                    Hidden text
                    {#if capacity?.imageOk}
                        <small class:over={utf8ByteLength(watermarkText) > capacity.maxTextBytes}>
                            {utf8ByteLength(watermarkText)} / {capacity.maxTextBytes} B
                        </small>
                    {/if}
                </span>
                <input
                    type="text"
                    bind:value={watermarkText}
                    placeholder="Text to hide in the image"
                    disabled={embedInputDisabled()}
                />
            </label>

            <button
                class="btn btn-primary"
                onclick={embedWatermark}
                disabled={embedProcessing || capacityChecking || (capacity !== null && !capacity.imageOk) || embedInputDisabled()}
            >
                {embedProcessing ? 'Processing...' : 'Generate watermarked PNG'}
            </button>

            {#if embedError}
                <div class="alert alert-error">{embedError}</div>
            {/if}
        </section>

        {#if resultImageUrl}
            <section class="panel result-panel">
                <h2>Watermarked image</h2>
                {#if classification}
                    <div class="notice">
                        AI classification: <strong>{classification.category}</strong>
                        {#if classification.categoryConfidence !== null}
                            ({classification.categoryConfidence}%)
                        {/if}
                        {#if classification.label}
                            - {classification.label}
                        {/if}
                    </div>
                {:else}
                    <div class="notice">AI classification was skipped or unavailable.</div>
                {/if}
                <div class="image-frame">
                    <img src={resultImageUrl} alt="Watermarked output" />
                </div>
                <a href={resultImageUrl} download="watermarked_image.png" class="download-link">
                    <button class="btn btn-success">Download PNG</button>
                </a>
            </section>
        {/if}
    {:else if activeTab === 'detect'}
        <section class="panel">
            <div class="panel-heading">
                <h2>Detect watermark</h2>
                <span class="cost">DETECT: {operationCost('DETECT')} token</span>
            </div>
            {@render OperationGate('DETECT')}
            <label class="field">
                <span>Image</span>
                <input type="file" accept="image/png,image/jpeg" bind:files={detectFiles} disabled={!canUseOperation('DETECT')} />
            </label>
            <button class="btn btn-primary" onclick={detectWatermark} disabled={detectProcessing || !canUseOperation('DETECT')}>
                {detectProcessing ? 'Checking...' : 'Detect'}
            </button>
            {#if detectError}<div class="alert alert-error">{detectError}</div>{/if}
        </section>

        {#if detectResult}
            <section class="panel result-panel">
                {#if detectResult.watermarked}
                    <div class="status yes">Watermark detected</div>
                    <div class="details">
                        <div><span>Owner</span><strong>{detectResult.ownerIdentity}</strong></div>
                        <div><span>Payload tier</span><strong>{detectResult.lengthBits ?? 'unknown'}</strong></div>
                    </div>
                {:else}
                    <div class="status no">No watermark detected</div>
                {/if}
            </section>
        {/if}
    {:else if activeTab === 'extract'}
        <section class="panel">
            <div class="panel-heading">
                <h2>Extract hidden text</h2>
                <span class="cost">EXTRACT: {operationCost('EXTRACT')} tokens</span>
            </div>
            {@render OperationGate('EXTRACT')}
            <label class="field">
                <span>Watermarked image</span>
                <input type="file" accept="image/png,image/jpeg" bind:files={extractFiles} disabled={!canUseOperation('EXTRACT')} />
            </label>
            <button class="btn btn-primary" onclick={extractWatermark} disabled={extractProcessing || !canUseOperation('EXTRACT')}>
                {extractProcessing ? 'Reading...' : 'Extract'}
            </button>
            {#if extractNotice}<div class="alert alert-info">{extractNotice}</div>{/if}
            {#if extractError}<div class="alert alert-error">{extractError}</div>{/if}
        </section>

        {#if extractResult}
            <section class="panel result-panel">
                <h2>Hidden text</h2>
                <div class="details">
                    <div><span>Owner</span><strong>{extractResult.ownerIdentity}</strong></div>
                </div>
                <pre>{extractResult.text}</pre>
            </section>
        {/if}
    {:else if activeTab === 'visualize'}
        <section class="panel">
            <div class="panel-heading">
                <h2>Visualize watermark footprint</h2>
                <span class="cost">VISUALIZE: {operationCost('VISUALIZE')} tokens</span>
            </div>
            {@render OperationGate('VISUALIZE')}
            <label class="field">
                <span>Image</span>
                <input type="file" accept="image/png,image/jpeg" bind:files={visualizeFiles} disabled={!canUseOperation('VISUALIZE')} />
            </label>
            <button class="btn btn-primary" onclick={visualizeWatermark} disabled={visualizeProcessing || !canUseOperation('VISUALIZE')}>
                {visualizeProcessing ? 'Rendering...' : 'Visualize'}
            </button>
            {#if visualizeError}<div class="alert alert-error">{visualizeError}</div>{/if}
        </section>

        {#if visualizeImageUrl}
            <section class="panel result-panel">
                <h2>Visualization</h2>
                <div class="image-frame">
                    <img src={visualizeImageUrl} alt="Watermark visualization" />
                </div>
                <a href={visualizeImageUrl} download="watermark_visualization.png" class="download-link">
                    <button class="btn btn-success">Download visualization</button>
                </a>
            </section>
        {/if}
    {/if}
</main>

{#snippet OperationGate(operation)}
    {@const status = actionStatus(operation)}
    {#if status}
        <div class="alert alert-warning">{status}</div>
    {:else}
        <div class="notice">{operationName(operation)} is available for your plan.</div>
    {/if}
{/snippet}

<style>
    :global(body) {
        margin: 0;
        background: #f3f5f7;
        color: #1f2933;
        font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }

    .container {
        max-width: 980px;
        margin: 0 auto;
        padding: 32px 20px 48px;
    }

    .topbar {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 16px;
        margin-bottom: 20px;
    }

    h1, h2 {
        margin: 0;
        color: #172033;
    }

    h1 {
        font-size: 1.8rem;
    }

    h2 {
        font-size: 1.25rem;
    }

    p {
        margin: 6px 0 0;
        color: #667085;
    }

    .account-panel {
        display: grid;
        grid-template-columns: repeat(4, minmax(120px, 1fr)) auto;
        gap: 10px;
        align-items: stretch;
        margin-bottom: 18px;
    }

    .metric, .panel, .tab {
        background: #fff;
        border: 1px solid #d9e2ec;
        border-radius: 8px;
    }

    .metric {
        padding: 12px 14px;
    }

    .metric span, .details span, .capacity-grid span {
        display: block;
        color: #667085;
        font-size: 0.78rem;
        font-weight: 700;
        text-transform: uppercase;
    }

    .metric strong {
        display: block;
        margin-top: 4px;
        font-size: 1.2rem;
    }

    .tabs {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 8px;
        margin-bottom: 18px;
    }

    .tab {
        padding: 12px 10px;
        color: #344054;
        font-weight: 700;
        cursor: pointer;
    }

    .tab.active {
        background: #2563eb;
        border-color: #2563eb;
        color: #fff;
    }

    .tab:disabled {
        background: #eef2f6;
        color: #98a2b3;
        cursor: not-allowed;
    }

    .tab.active:disabled {
        background: #98a2b3;
        border-color: #98a2b3;
        color: #fff;
    }

    .panel {
        padding: 22px;
        margin-bottom: 18px;
    }

    .panel-heading {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
        margin-bottom: 18px;
    }

    .cost {
        color: #155e75;
        background: #ecfeff;
        border: 1px solid #a5f3fc;
        border-radius: 999px;
        padding: 5px 10px;
        font-size: 0.84rem;
        font-weight: 700;
        white-space: nowrap;
    }

    .field {
        display: grid;
        gap: 8px;
        margin-bottom: 16px;
        font-weight: 700;
    }

    .field span {
        display: flex;
        justify-content: space-between;
        gap: 12px;
    }

    input[type="text"], input[type="file"] {
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        background: #f8fafc;
        padding: 12px 14px;
        font-size: 1rem;
    }

    input[type="text"]:focus {
        outline: none;
        border-color: #2563eb;
        background: #fff;
    }

    input:disabled {
        background: #eef2f6;
        color: #98a2b3;
        cursor: not-allowed;
    }

    .btn {
        border: 0;
        border-radius: 8px;
        padding: 11px 16px;
        font-weight: 800;
        cursor: pointer;
    }

    .btn-primary, .btn-success {
        width: 100%;
        color: #fff;
    }

    .btn-primary {
        background: #2563eb;
    }

    .btn-success {
        background: #16803c;
    }

    .btn-outline {
        background: #fff;
        color: #344054;
        border: 1px solid #cbd5e1;
    }

    .compact {
        width: auto;
        align-self: center;
    }

    .btn:disabled {
        background: #98a2b3;
        cursor: not-allowed;
    }

    .alert, .notice {
        border-radius: 8px;
        padding: 11px 13px;
        margin: 12px 0;
        font-size: 0.93rem;
    }

    .notice {
        background: #f8fafc;
        border: 1px solid #d9e2ec;
        color: #475467;
    }

    .alert-error {
        background: #fff1f2;
        border: 1px solid #fecdd3;
        color: #be123c;
    }

    .alert-warning {
        background: #fffbeb;
        border: 1px solid #fde68a;
        color: #92400e;
    }

    .alert-info {
        background: #eff6ff;
        border: 1px solid #bfdbfe;
        color: #1d4ed8;
    }

    .capacity-grid, .details {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 10px;
        margin-bottom: 12px;
    }

    .capacity-grid div, .details div {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 10px;
    }

    .capacity-grid div.blocked {
        background: #fff7ed;
        border-color: #fdba74;
    }

    .capacity-grid div.blocked strong {
        color: #9a3412;
    }

    small {
        color: #667085;
        font-weight: 700;
    }

    small.over {
        color: #be123c;
    }

    .result-panel {
        text-align: center;
    }

    .image-frame {
        margin: 16px 0;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
        padding: 10px;
        background: #f8fafc;
    }

    .image-frame img {
        display: block;
        max-width: 100%;
        height: auto;
        margin: 0 auto;
        border-radius: 4px;
    }

    .download-link {
        text-decoration: none;
    }

    .status {
        display: inline-block;
        border-radius: 999px;
        padding: 9px 16px;
        font-weight: 800;
        margin-bottom: 12px;
    }

    .status.yes {
        background: #dcfce7;
        color: #166534;
    }

    .status.no {
        background: #fee2e2;
        color: #991b1b;
    }

    pre {
        text-align: left;
        white-space: pre-wrap;
        word-break: break-word;
        background: #0f172a;
        color: #e2e8f0;
        border-radius: 8px;
        padding: 16px;
    }

    @media (max-width: 760px) {
        .topbar, .panel-heading {
            flex-direction: column;
            align-items: stretch;
        }

        .account-panel, .tabs, .capacity-grid, .details {
            grid-template-columns: 1fr;
        }

        .compact {
            width: 100%;
        }
    }
</style>
