<script>
    // W Svelte 5 zmienne, które zmieniają się i wpływają na widok, muszą używać $state()
    let email = $state('admin@gmail.com');
    let password = $state('admin');
    let errorMessage = $state('');

    async function handleLogin(event) {
        event.preventDefault();
        errorMessage = '';

        try {
            const response = await fetch('http://localhost:8081/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });

            if (response.ok) {
                const data = await response.json();
                localStorage.setItem('jwt_token', data.token);
                window.location.href = '/';
            } else {
                errorMessage = await response.text() || 'Błąd logowania';
            }
        } catch (error) {
            errorMessage = 'Błąd połączenia z serwerem autoryzacji.';
        }
    }
</script>

<main style="max-width: 400px; margin: 50px auto; font-family: sans-serif;">
    <h2>Logowanie</h2>
    <form onsubmit={handleLogin} style="display: flex; flex-direction: column; gap: 10px;">
        <label>
            Email:
            <input type="email" bind:value={email} required />
        </label>
        <label>
            Hasło:
            <input type="password" bind:value={password} required />
        </label>
        <button type="submit">Zaloguj</button>
    </form>
    {#if errorMessage}
        <p style="color: red;">{errorMessage}</p>
    {/if}
</main>