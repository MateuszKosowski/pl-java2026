<script>
    import { onMount } from 'svelte';

    // Dodanie $state() do zmiennych reaktywnych
    let token = $state('');
    let files = $state();
    let watermarkText = $state('');
    let isProcessing = $state(false);
    let resultImageUrl = $state(null);
    let errorMessage = $state('');

    onMount(() => {
        token = localStorage.getItem('jwt_token');
        if (!token) {
            window.location.href = '/login';
        }
    });

    async function embedWatermark() {
        if (!files || files.length === 0) {
            errorMessage = 'Wybierz obraz.';
            return;
        }

        errorMessage = '';
        isProcessing = true;
        resultImageUrl = null;

        const formData = new FormData();
        formData.append('image', files[0]);
        formData.append('text', watermarkText);

        try {
            const response = await fetch('http://localhost:8082/api/watermark/embed', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                },
                body: formData
            });

            if (response.ok) {
                const blob = await response.blob();
                resultImageUrl = URL.createObjectURL(blob);
            } else {
                const errorData = await response.json();
                errorMessage = errorData.error || 'Błąd podczas przetwarzania obrazu.';
            }
        } catch (error) {
            errorMessage = 'Błąd połączenia z serwerem.';
        } finally {
            isProcessing = false;
        }
    }

    function logout() {
        localStorage.removeItem('jwt_token');
        window.location.href = '/login';
    }
</script>

<main style="max-width: 600px; margin: 50px auto; font-family: sans-serif;">
    <div style="display: flex; justify-content: space-between; align-items: center;">
        <h2>System Znaków Wodnych</h2>
        <button onclick={logout}>Wyloguj</button>
    </div>

    <div style="border: 1px solid #ccc; padding: 20px; border-radius: 8px;">
        <h3>Osadź znak wodny</h3>

        <div style="display: flex; flex-direction: column; gap: 15px;">
            <label>
                Obraz bazowy (PNG/JPG):
                <input type="file" accept="image/png, image/jpeg" bind:files />
            </label>

            <label>
                Ukryty tekst:
                <input type="text" bind:value={watermarkText} placeholder="Wpisz tajną wiadomość" />
            </label>

            <button onclick={embedWatermark} disabled={isProcessing}>
                {isProcessing ? 'Przetwarzanie...' : 'Generuj obraz'}
            </button>
        </div>

        {#if errorMessage}
            <p style="color: red; margin-top: 15px;">{errorMessage}</p>
        {/if}
    </div>

    {#if resultImageUrl}
        <div style="margin-top: 30px; text-align: center;">
            <h3>Wynik:</h3>
            <img src={resultImageUrl} alt="Obraz ze znakiem wodnym" style="max-width: 100%; border: 1px solid #eee;" />
            <br />
            <a href={resultImageUrl} download="watermarked_image.png">
                <button style="margin-top: 10px;">Pobierz obraz</button>
            </a>
        </div>
    {/if}
</main>