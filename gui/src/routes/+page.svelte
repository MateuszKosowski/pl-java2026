<script>
    import { onMount } from 'svelte';

    let token = $state('');

    /** @type {'embed' | 'detect' | 'extract' | 'visualize'} */
    let activeTab = $state('embed');

    const tabs = /** @type {const} */ ([
        { id: 'embed', label: '✨ Osadź' },
        { id: 'detect', label: '🔍 Wykryj' },
        { id: 'extract', label: '📖 Wyodrębnij' },
        { id: 'visualize', label: '🗺️ Wizualizuj' }
    ]);

    // --- Embed ---
    let embedFiles = $state();
    let watermarkText = $state('');
    let embedProcessing = $state(false);
    /** @type {string | null} */
    let resultImageUrl = $state(null);
    /** @type {{ category: string, label: string | null, confidence: number | null, categoryConfidence: number | null } | null} */
    let classification = $state(null);
    let embedError = $state('');
    /** @type {{ maxTextBytes: number, minImageWidth: number, minImageHeight: number, imageWidth: number, imageHeight: number, imageOk: boolean, lengthBits: number } | null} */
    let capacity = $state(null);
    let capacityChecking = $state(false);
    /** @type {string | null} */
    let capacityError = $state(null);
    /** @type {AbortController | null} */
    let capacityAbort = null;

    // --- Detect ---
    let detectFiles = $state();
    let detectProcessing = $state(false);
    /** @type {{ watermarked: boolean, ownerIdentity: string | null, version: number | null } | null} */
    let detectResult = $state(null);
    let detectError = $state('');

    // --- Extract ---
    let extractFiles = $state();
    let extractProcessing = $state(false);
    /** @type {{ ownerIdentity: string, text: string } | null} */
    let extractResult = $state(null);
    let extractNotice = $state('');
    let extractError = $state('');

    // --- Visualize ---
    let visualizeFiles = $state();
    let visualizeProcessing = $state(false);
    /** @type {string | null} */
    let visualizeImageUrl = $state(null);
    let visualizeNotice = $state('');
    let visualizeError = $state('');

    onMount(() => {
        token = localStorage.getItem('jwt_token') ?? '';
        if (!token) {
            window.location.href = '/login';
        }
    });

    function logout() {
        localStorage.removeItem('jwt_token');
        window.location.href = '/login';
    }

    /** @param {FileList | undefined} files */
    function validateImage(files) {
        if (!files || files.length === 0) {
            return 'Wybierz obraz z dysku.';
        }
        if (files[0].size < 100) {
            return 'Wybrany plik jest zbyt mały lub uszkodzony.';
        }
        return '';
    }

    // Shared POST helper. Sends the selected image (and optional text) with the JWT.
    /** @param {string} url @param {File} image @param {string} [text] */
    function postImage(url, image, text) {
        const formData = new FormData();
        formData.append('image', image);
        if (text !== undefined) {
            formData.append('text', text);
        }
        return fetch(url, {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
            body: formData
        });
    }

    /** @param {Response} response */
    async function readError(response) {
        try {
            const data = await response.json();
            return data.error || data.message || 'Wystąpił błąd podczas przetwarzania.';
        } catch {
            return 'Wystąpił błąd podczas przetwarzania.';
        }
    }

    // Bytes-not-chars: backend measures payload in UTF-8 bytes, so multibyte
    // characters (ą, ł, emoji) eat more capacity than ASCII. Mirror that here.
    const textEncoder = new TextEncoder();
    /** @param {string} text */
    function utf8ByteLength(text) {
        return textEncoder.encode(text).length;
    }

    let lastProbedFileSignature = '';

    /** @param {File} file */
    function fileSignature(file) {
        return `${file.name}|${file.size}|${file.lastModified}`;
    }

    async function probeCapacity() {
        if (!embedFiles || embedFiles.length === 0) return;
        // Cancel any in-flight probe so an earlier file's response can't overwrite
        // state for a newer selection.
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
        } catch (err) {
            if (err instanceof DOMException && err.name === 'AbortError') return;
            capacityError = 'Nie udało się sprawdzić pojemności obrazu.';
        } finally {
            if (capacityAbort === controller) {
                capacityAbort = null;
                capacityChecking = false;
            }
        }
    }

    $effect(() => {
        // Refetch capacity only when the user actually picked a new file —
        // re-binding the same FileList shouldn't trigger another upload.
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

    async function embedWatermark() {
        const validation = validateImage(embedFiles);
        if (validation) { embedError = validation; return; }
        if (!watermarkText || watermarkText.trim() === '') {
            embedError = 'Wpisz tekst, który chcesz ukryć w obrazie.';
            return;
        }
        if (capacityChecking) {
            embedError = 'Poczekaj na sprawdzenie pojemności obrazu.';
            return;
        }
        if (capacity && !capacity.imageOk) {
            embedError = `Obraz jest za mały (${capacity.imageWidth}×${capacity.imageHeight}). Minimum to ${capacity.minImageWidth}×${capacity.minImageHeight} px.`;
            return;
        }
        if (capacity && utf8ByteLength(watermarkText) > capacity.maxTextBytes) {
            embedError = `Tekst jest za długi — maksymalnie ${capacity.maxTextBytes} bajtów dla tego obrazu.`;
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
            } else {
                embedError = await readError(response);
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
        } catch {
            detectError = 'Błąd połączenia z serwerem.';
        } finally {
            detectProcessing = false;
        }
    }

    // Extract checks for a watermark first, mirroring the visualize flow: an image
    // with nothing hidden gets a friendly notice instead of a raw backend error.
    async function extractWatermark() {
        const validation = validateImage(extractFiles);
        if (validation) { extractError = validation; return; }

        extractError = '';
        extractNotice = '';
        extractProcessing = true;
        extractResult = null;

        try {
            const detectResponse = await postImage('/api/watermark/detect', extractFiles[0]);
            if (!detectResponse.ok) {
                extractError = await readError(detectResponse);
                return;
            }

            const detection = await detectResponse.json();
            if (!detection.watermarked) {
                extractNotice = 'Ten obraz nie zawiera ukrytego znaku wodnego – nie ma czego odczytać.';
                return;
            }

            const response = await postImage('/api/watermark/extract', extractFiles[0]);
            if (response.ok) {
                extractResult = await response.json();
            } else if (response.status === 403) {
                extractError = 'Nie jesteś właścicielem tego znaku wodnego – nie możesz odczytać ukrytej treści.';
            } else {
                extractError = await readError(response);
            }
        } catch {
            extractError = 'Błąd połączenia z serwerem.';
        } finally {
            extractProcessing = false;
        }
    }

    // Visualize runs detect first: there is nothing to highlight in an image that
    // carries no watermark, so we short-circuit with a friendly notice instead.
    async function visualizeWatermark() {
        const validation = validateImage(visualizeFiles);
        if (validation) { visualizeError = validation; return; }

        visualizeError = '';
        visualizeNotice = '';
        visualizeProcessing = true;
        visualizeImageUrl = null;

        try {
            const detectResponse = await postImage('/api/watermark/detect', visualizeFiles[0]);
            if (!detectResponse.ok) {
                visualizeError = await readError(detectResponse);
                return;
            }

            const detection = await detectResponse.json();
            if (!detection.watermarked) {
                visualizeNotice = 'Ten obraz nie zawiera ukrytego znaku wodnego – nie ma czego wizualizować.';
                return;
            }

            const response = await postImage('/api/watermark/visualize', visualizeFiles[0]);
            if (response.ok) {
                visualizeImageUrl = URL.createObjectURL(await response.blob());
            } else {
                visualizeError = await readError(response);
            }
        } catch {
            visualizeError = 'Błąd połączenia z serwerem.';
        } finally {
            visualizeProcessing = false;
        }
    }

    // Reads the image classification that ai-service produced during embedding.
    // The backend exposes it via response headers; returns null when unavailable.
    /** @param {Headers} headers */
    function readClassification(headers) {
        const category = headers.get('X-Image-Category');
        const label = headers.get('X-Image-Label');
        const confidence = headers.get('X-Image-Confidence');
        const categoryConfidence = headers.get('X-Image-Category-Confidence');

        if (!category || category === 'unknown') {
            return null;
        }

        const toPercent = (v) => (v ? Math.round(parseFloat(v) * 100) : null);

        return {
            category,
            label: label && label !== 'unknown' ? label : null,
            confidence: toPercent(confidence),
            categoryConfidence: toPercent(categoryConfidence)
        };
    }
</script>

<main class="container">
    <header class="header">
        <h2>🔒 Znakowanie Obrazów</h2>
        <button class="btn btn-outline" onclick={logout}>Wyloguj</button>
    </header>

    <nav class="tabs">
        {#each tabs as tab}
            <button
                class="tab"
                class:active={activeTab === tab.id}
                onclick={() => (activeTab = tab.id)}
            >
                {tab.label}
            </button>
        {/each}
    </nav>

    {#if activeTab === 'embed'}
        <div class="card">
            <h3>Osadź znak wodny</h3>
            <p class="subtitle">Wybierz obraz PNG i wpisz tajną wiadomość, która zostanie w nim niewidocznie ukryta.</p>

            <p class="hint">Plik wynikowy zostaje PNG-iem — kompresja JPG, screenshot lub edycja usuwa znak wodny.</p>

            <div class="form-group">
                <label for="embed-file">Obraz bazowy (PNG):</label>
                <input id="embed-file" type="file" accept="image/png" bind:files={embedFiles} class="input-file" />
            </div>

            {#if capacityChecking}
                <div class="capacity-info capacity-info--pending">⏳ Sprawdzanie pojemności obrazu…</div>
            {:else if capacityError}
                <div class="capacity-info capacity-info--error">⚠️ {capacityError}</div>
            {:else if capacity && !capacity.imageOk}
                <div class="capacity-info capacity-info--error">
                    ⚠️ Obraz jest za mały: <strong>{capacity.imageWidth}×{capacity.imageHeight} px</strong>.
                    Minimum to <strong>{capacity.minImageWidth}×{capacity.minImageHeight} px</strong>.
                </div>
            {:else if capacity}
                <div class="capacity-info" role="status" aria-live="polite">
                    📐 <strong>{capacity.imageWidth}×{capacity.imageHeight} px</strong> — możesz ukryć maksymalnie
                    <strong>{capacity.maxTextBytes} bajtów</strong> (znaki ASCII = 1 bajt, polskie znaki = 2 bajty).
                </div>
            {/if}

            <div class="form-group">
                <label for="watermark-text">
                    Ukryty tekst:
                    {#if capacity && capacity.imageOk}
                        <span class="char-counter" class:char-counter--over={utf8ByteLength(watermarkText) > capacity.maxTextBytes}>
                            {utf8ByteLength(watermarkText)} / {capacity.maxTextBytes} B
                        </span>
                    {/if}
                </label>
                <input id="watermark-text" type="text" bind:value={watermarkText} placeholder="Np. Prawa autorskie - Jan Kowalski" class="input-text" />
            </div>

            <button class="btn btn-primary" onclick={embedWatermark} disabled={embedProcessing || capacityChecking || (capacity !== null && !capacity.imageOk)}>
                {embedProcessing ? '⏳ Przetwarzanie...' : '✨ Generuj obraz'}
            </button>

            {#if embedError}
                <div class="alert alert-error"><strong>Błąd:</strong> {embedError}</div>
            {/if}
        </div>

        {#if resultImageUrl}
            <div class="card result-card">
                <h3>Oto twój zabezpieczony obraz:</h3>

                {#if classification}
                    <div class="classification">
                        <span class="classification-label">Wykryta klasa:</span>
                        <span class="classification-category">{classification.category}</span>
                        {#if classification.categoryConfidence !== null && classification.categoryConfidence > 0}
                            <span class="classification-confidence">{classification.categoryConfidence}%</span>
                        {/if}
                        {#if classification.label}
                            <span class="classification-detail">
                                · {classification.label}{classification.confidence !== null ? ` (${classification.confidence}%)` : ''}
                            </span>
                        {/if}
                    </div>
                {/if}

                <div class="image-wrapper">
                    <img src={resultImageUrl} alt="Obraz ze znakiem wodnym" />
                </div>
                <a href={resultImageUrl} download="watermarked_image.png" class="download-link">
                    <button class="btn btn-success">⬇️ Pobierz obraz (PNG)</button>
                </a>
                <p class="hint hint--result">Trzymaj jako PNG — rekompresja niszczy znak.</p>
            </div>
        {/if}
    {:else if activeTab === 'detect'}
        <div class="card">
            <h3>Wykryj znak wodny</h3>
            <p class="subtitle">Sprawdź, czy obraz zawiera znak wodny osadzony przez ten serwis. Działa tylko na nieprzetworzonym PNG-u.</p>

            <div class="form-group">
                <label for="detect-file">Obraz do sprawdzenia (PNG/JPG):</label>
                <input id="detect-file" type="file" accept="image/png, image/jpeg" bind:files={detectFiles} class="input-file" />
            </div>

            <button class="btn btn-primary" onclick={detectWatermark} disabled={detectProcessing}>
                {detectProcessing ? '⏳ Sprawdzanie...' : '🔍 Sprawdź'}
            </button>

            {#if detectError}
                <div class="alert alert-error"><strong>Błąd:</strong> {detectError}</div>
            {/if}
        </div>

        {#if detectResult}
            <div class="card result-card">
                {#if detectResult.watermarked}
                    <div class="status-badge status-yes">✅ Wykryto znak wodny</div>
                    <div class="meta">
                        {#if detectResult.ownerIdentity}
                            <div><span class="meta-key">Właściciel:</span> {detectResult.ownerIdentity}</div>
                        {/if}
                        {#if detectResult.version !== null}
                            <div><span class="meta-key">Wersja formatu:</span> {detectResult.version}</div>
                        {/if}
                    </div>
                {:else}
                    <div class="status-badge status-no">❌ Brak znaku wodnego</div>
                    <p class="subtitle">Ten obraz nie zawiera znaku osadzonego przez ten serwis.</p>
                {/if}
            </div>
        {/if}
    {:else if activeTab === 'extract'}
        <div class="card">
            <h3>Wyodrębnij ukryty tekst</h3>
            <p class="subtitle">Odczytaj treść ukrytą w obrazie. Dostęp ma tylko właściciel znaku wodnego. Działa tylko na nieprzetworzonym PNG-u.</p>

            <div class="form-group">
                <label for="extract-file">Obraz ze znakiem (PNG/JPG):</label>
                <input id="extract-file" type="file" accept="image/png, image/jpeg" bind:files={extractFiles} class="input-file" />
            </div>

            <button class="btn btn-primary" onclick={extractWatermark} disabled={extractProcessing}>
                {extractProcessing ? '⏳ Odczytywanie...' : '📖 Wyodrębnij tekst'}
            </button>

            {#if extractNotice}
                <div class="alert alert-info">{extractNotice}</div>
            {/if}
            {#if extractError}
                <div class="alert alert-error"><strong>Błąd:</strong> {extractError}</div>
            {/if}
        </div>

        {#if extractResult}
            <div class="card result-card">
                <h3>Ukryta treść:</h3>
                <div class="meta">
                    <div><span class="meta-key">Właściciel:</span> {extractResult.ownerIdentity}</div>
                </div>
                <div class="extracted-text">{extractResult.text}</div>
            </div>
        {/if}
    {:else if activeTab === 'visualize'}
        <div class="card">
            <h3>Wizualizuj znak wodny</h3>
            <p class="subtitle">Mapa cieplna pokazuje, gdzie w obrazie zostały zapisane dane. Działa tylko na nieprzetworzonym PNG-u.</p>

            <div class="form-group">
                <label for="visualize-file">Obraz do wizualizacji (PNG/JPG):</label>
                <input id="visualize-file" type="file" accept="image/png, image/jpeg" bind:files={visualizeFiles} class="input-file" />
            </div>

            <button class="btn btn-primary" onclick={visualizeWatermark} disabled={visualizeProcessing}>
                {visualizeProcessing ? '⏳ Przetwarzanie...' : '🗺️ Wizualizuj'}
            </button>

            {#if visualizeNotice}
                <div class="alert alert-info">{visualizeNotice}</div>
            {/if}
            {#if visualizeError}
                <div class="alert alert-error"><strong>Błąd:</strong> {visualizeError}</div>
            {/if}
        </div>

        {#if visualizeImageUrl}
            <div class="card result-card">
                <h3>Lokalizacja danych znaku wodnego:</h3>
                <div class="image-wrapper">
                    <img src={visualizeImageUrl} alt="Wizualizacja bloków znaku wodnego" />
                </div>
                <a href={visualizeImageUrl} download="watermark_visualization.png" class="download-link">
                    <button class="btn btn-success">⬇️ Pobierz wizualizację</button>
                </a>
            </div>
        {/if}
    {/if}
</main>

<style>
    :global(body) {
        margin: 0;
        padding: 0;
        background-color: #f4f7f6;
        font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        color: #333;
    }

    .container {
        max-width: 650px;
        margin: 40px auto;
        padding: 0 20px;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 30px;
    }

    .header h2 {
        margin: 0;
        color: #2c3e50;
    }

    .tabs {
        display: flex;
        gap: 8px;
        margin-bottom: 30px;
        flex-wrap: wrap;
    }

    .tab {
        flex: 1 1 auto;
        padding: 10px 14px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background-color: #ffffff;
        color: #4a5568;
        font-size: 0.95rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.2s ease;
    }

    .tab:hover:not(.active) {
        background-color: #edf2f7;
    }

    .tab.active {
        background-color: #3182ce;
        border-color: #3182ce;
        color: #ffffff;
    }

    .card {
        background: #ffffff;
        border-radius: 12px;
        padding: 30px;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05), 0 1px 3px rgba(0, 0, 0, 0.1);
        margin-bottom: 30px;
    }

    .card h3 {
        margin-top: 0;
        margin-bottom: 10px;
        font-size: 1.5rem;
        color: #1a202c;
    }

    .subtitle {
        color: #718096;
        font-size: 0.95rem;
        margin-bottom: 25px;
    }

    .form-group {
        margin-bottom: 20px;
        display: flex;
        flex-direction: column;
    }

    .form-group label {
        font-weight: 600;
        margin-bottom: 8px;
        font-size: 0.9rem;
        color: #4a5568;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
    }

    .capacity-info {
        background-color: #ebf8ff;
        border: 1px solid #bee3f8;
        color: #2c5282;
        padding: 10px 14px;
        border-radius: 8px;
        font-size: 0.9rem;
        margin-bottom: 20px;
    }

    .capacity-info--pending {
        background-color: #f7fafc;
        border-color: #e2e8f0;
        color: #718096;
    }

    .capacity-info--error {
        background-color: #fff5f5;
        border-color: #feb2b2;
        color: #9b2c2c;
    }

    .char-counter {
        font-size: 0.8rem;
        font-weight: 500;
        color: #718096;
        font-variant-numeric: tabular-nums;
    }

    .char-counter--over {
        color: #c53030;
        font-weight: 700;
    }

    .input-text, .input-file {
        padding: 12px 16px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        font-size: 1rem;
        transition: border-color 0.2s;
        background-color: #f8fafc;
    }

    .input-text:focus {
        outline: none;
        border-color: #3182ce;
        background-color: #ffffff;
    }

    .btn {
        padding: 12px 20px;
        border: none;
        border-radius: 8px;
        font-size: 1rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.2s ease;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 100%;
    }

    .btn-primary {
        background-color: #3182ce;
        color: white;
        margin-top: 10px;
    }

    .btn-primary:hover:not(:disabled) {
        background-color: #2b6cb0;
        transform: translateY(-1px);
    }

    .btn-success {
        background-color: #38a169;
        color: white;
    }

    .btn-success:hover {
        background-color: #2f855a;
        transform: translateY(-1px);
    }

    .btn-outline {
        background-color: transparent;
        border: 2px solid #e2e8f0;
        color: #4a5568;
        width: auto;
        padding: 8px 16px;
    }

    .btn-outline:hover {
        background-color: #edf2f7;
    }

    .btn:disabled {
        background-color: #a0aec0;
        cursor: not-allowed;
    }

    .alert {
        margin-top: 20px;
        padding: 12px 16px;
        border-radius: 8px;
        font-size: 0.95rem;
    }

    .alert-error {
        background-color: #fff5f5;
        color: #c53030;
        border-left: 4px solid #f56565;
    }

    .alert-info {
        background-color: #ebf8ff;
        color: #2b6cb0;
        border-left: 4px solid #4299e1;
    }

    .hint {
        margin: -8px 0 16px;
        font-size: 0.8rem;
        color: #64748b; /* slate-500, ~5.7:1 on white — meets WCAG AA */
        line-height: 1.45;
    }

    .hint--result {
        margin: 14px 0 0;
        text-align: center;
    }

    .result-card {
        text-align: center;
    }

    .classification {
        display: inline-flex;
        align-items: center;
        flex-wrap: wrap;
        justify-content: center;
        gap: 8px;
        margin: 8px auto 4px;
        padding: 10px 16px;
        background-color: #ebf8ff;
        border: 1px solid #bee3f8;
        border-radius: 8px;
        font-size: 0.95rem;
    }

    .classification-label {
        color: #4a5568;
        font-weight: 600;
    }

    .classification-category {
        color: #2b6cb0;
        font-weight: 700;
        text-transform: capitalize;
    }

    .classification-detail {
        color: #718096;
    }

    .classification-confidence {
        color: #2f855a;
        font-weight: 600;
        background-color: #f0fff4;
        border-radius: 999px;
        padding: 2px 10px;
    }

    .status-badge {
        display: inline-block;
        padding: 10px 20px;
        border-radius: 999px;
        font-weight: 700;
        font-size: 1.05rem;
        margin-bottom: 8px;
    }

    .status-yes {
        background-color: #f0fff4;
        color: #2f855a;
        border: 1px solid #9ae6b4;
    }

    .status-no {
        background-color: #fff5f5;
        color: #c53030;
        border: 1px solid #feb2b2;
    }

    .meta {
        color: #4a5568;
        font-size: 0.95rem;
        margin: 8px 0;
        line-height: 1.6;
    }

    .meta-key {
        font-weight: 600;
        color: #718096;
    }

    .extracted-text {
        margin-top: 16px;
        padding: 16px;
        background-color: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
        color: #1a202c;
        text-align: left;
        white-space: pre-wrap;
        word-break: break-word;
    }

    .image-wrapper {
        margin: 20px 0;
        border-radius: 8px;
        overflow: hidden;
        border: 2px dashed #e2e8f0;
        padding: 10px;
    }

    .image-wrapper img {
        max-width: 100%;
        height: auto;
        display: block;
        border-radius: 4px;
        margin: 0 auto;
    }

    .download-link {
        text-decoration: none;
    }
</style>
