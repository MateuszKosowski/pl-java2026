<script>
    import { onMount } from 'svelte';

    let token = $state('');
    let files = $state();
    let watermarkText = $state('');
    let isProcessing = $state(false);
    /** @type {string | null} */
    let resultImageUrl = $state(null);
    /** @type {{ category: string, label: string | null, confidence: number | null } | null} */
    let classification = $state(null);
    let errorMessage = $state('');

    onMount(() => {
        token = localStorage.getItem('jwt_token') ?? '';
        if (!token) {
            window.location.href = '/login';
        }
    });

    async function embedWatermark() {
        if (!files || files.length === 0) {
            errorMessage = 'Wybierz obraz z dysku.';
            return;
        }

        if (!watermarkText || watermarkText.trim() === '') {
            errorMessage = 'Wpisz tekst, który chcesz ukryć w obrazie.';
            return;
        }

        if (files[0].size < 100) {
             errorMessage = 'Wybrany plik jest zbyt mały lub uszkodzony.';
             return;
        }

        errorMessage = '';
        isProcessing = true;
        resultImageUrl = null;
        classification = null;

        const formData = new FormData();
        formData.append('image', files[0]);
        formData.append('text', watermarkText);

        try {
            const response = await fetch('/api/watermark/embed', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                },
                body: formData
            });

            if (response.ok) {
                classification = readClassification(response.headers);
                const blob = await response.blob();
                resultImageUrl = URL.createObjectURL(blob);
            } else {
                const errorData = await response.json();
                errorMessage = errorData.error || errorData.message || 'Błąd podczas przetwarzania obrazu.';
            }
        } catch (error) {
            errorMessage = 'Błąd połączenia z serwerem.';
        } finally {
            isProcessing = false;
        }
    }

    // Reads the image classification that ai-service produced during embedding.
    // The backend exposes it via response headers; returns null when unavailable.
    /** @param {Headers} headers */
    function readClassification(headers) {
        const category = headers.get('X-Image-Category');
        const label = headers.get('X-Image-Label');
        const confidence = headers.get('X-Image-Confidence');

        if (!category || category === 'unknown') {
            return null;
        }

        return {
            category,
            label: label && label !== 'unknown' ? label : null,
            confidence: confidence ? Math.round(parseFloat(confidence) * 100) : null
        };
    }

    function logout() {
        localStorage.removeItem('jwt_token');
        window.location.href = '/login';
    }
</script>

<main class="container">
    <header class="header">
        <h2>🔒 Znakowanie Obrazów</h2>
        <button class="btn btn-outline" onclick={logout}>Wyloguj</button>
    </header>

    <div class="card">
        <h3>Osadź znak wodny</h3>
        <p class="subtitle">Wybierz obraz i wpisz tajną wiadomość, która zostanie w nim niewidocznie ukryta.</p>

        <div class="form-group">
            <label for="file-upload">Obraz bazowy (PNG/JPG):</label>
            <input id="file-upload" type="file" accept="image/png, image/jpeg" bind:files class="input-file" />
        </div>

        <div class="form-group">
            <label for="watermark-text">Ukryty tekst:</label>
            <input id="watermark-text" type="text" bind:value={watermarkText} placeholder="Np. Prawa autorskie - Jan Kowalski" class="input-text" />
        </div>

        <button class="btn btn-primary" onclick={embedWatermark} disabled={isProcessing}>
            {isProcessing ? '⏳ Przetwarzanie...' : '✨ Generuj obraz'}
        </button>

        {#if errorMessage}
            <div class="alert alert-error">
                <strong>Błąd:</strong> {errorMessage}
            </div>
        {/if}
    </div>

    {#if resultImageUrl}
        <div class="card result-card">
            <h3>Oto twój zabezpieczony obraz:</h3>

            {#if classification}
                <div class="classification">
                    <span class="classification-label">Wykryta klasa:</span>
                    <span class="classification-category">{classification.category}</span>
                    {#if classification.label}
                        <span class="classification-detail">({classification.label})</span>
                    {/if}
                    {#if classification.confidence !== null}
                        <span class="classification-confidence">{classification.confidence}%</span>
                    {/if}
                </div>
            {/if}

            <div class="image-wrapper">
                <img src={resultImageUrl} alt="Obraz ze znakiem wodnym" />
            </div>
            <a href={resultImageUrl} download="watermarked_image.png" class="download-link">
                <button class="btn btn-success">⬇️ Pobierz obraz</button>
            </a>
        </div>
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