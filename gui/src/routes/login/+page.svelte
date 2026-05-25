<script>
    let email = $state('admin@gmail.com');
    let password = $state('admin');
    let errorMessage = $state('');
    let isLoggingIn = $state(false);

    /** @param {SubmitEvent} event */
    async function handleLogin(event) {
        event.preventDefault();
        errorMessage = '';
        isLoggingIn = true;

        try {
            const response = await fetch('/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });

            if (response.ok) {
                const data = await response.json();
                localStorage.setItem('jwt_token', data.token);
                window.location.href = '/';
            } else {
                const errorText = await response.text();
                errorMessage = errorText || 'Nieprawidłowy e-mail lub hasło.';
            }
        } catch (error) {
            errorMessage = 'Błąd połączenia z serwerem autoryzacji.';
        } finally {
            isLoggingIn = false;
        }
    }
</script>

<main class="container">
    <div class="login-wrapper">
        <header class="header">
            <h1>🔐 Logowanie</h1>
            <p>Witaj w Systemie Znaków Wodnych</p>
        </header>

        <div class="card">
            <form onsubmit={handleLogin}>
                <div class="form-group">
                    <label for="email">Adres e-mail</label>
                    <input
                        id="email"
                        type="email"
                        bind:value={email}
                        required
                        placeholder="twoj@email.com"
                        class="input-text"
                    />
                </div>

                <div class="form-group">
                    <label for="password">Hasło</label>
                    <input
                        id="password"
                        type="password"
                        bind:value={password}
                        required
                        placeholder="••••••••"
                        class="input-text"
                    />
                </div>

                <button type="submit" class="btn btn-primary" disabled={isLoggingIn}>
                    {isLoggingIn ? '⏳ Logowanie...' : 'Zaloguj się'}
                </button>
            </form>

            {#if errorMessage}
                <div class="alert alert-error">
                    {errorMessage}
                </div>
            {/if}
        </div>

        <footer class="footer-info">
            <p>Domyślne dane: admin@gmail.com / admin</p>
        </footer>
    </div>
</main>

<style>
    :global(body) {
        margin: 0;
        padding: 0;
        background-color: #f4f7f6;
        font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        color: #333;
    }

    .container {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        padding: 20px;
    }

    .login-wrapper {
        width: 100%;
        max-width: 420px;
    }

    .header {
        text-align: center;
        margin-bottom: 30px;
    }

    .header h1 {
        margin: 0;
        color: #2c3e50;
        font-size: 2rem;
    }

    .header p {
        color: #718096;
        margin-top: 8px;
    }

    .card {
        background: #ffffff;
        border-radius: 12px;
        padding: 40px;
        box-shadow: 0 10px 25px rgba(0, 0, 0, 0.05), 0 5px 10px rgba(0, 0, 0, 0.05);
    }

    .form-group {
        margin-bottom: 25px;
        display: flex;
        flex-direction: column;
    }

    .form-group label {
        font-weight: 600;
        margin-bottom: 8px;
        font-size: 0.9rem;
        color: #4a5568;
    }

    .input-text {
        padding: 12px 16px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        font-size: 1rem;
        transition: all 0.2s;
        background-color: #f8fafc;
    }

    .input-text:focus {
        outline: none;
        border-color: #3182ce;
        background-color: #ffffff;
        box-shadow: 0 0 0 3px rgba(49, 130, 206, 0.1);
    }

    .btn {
        padding: 14px 20px;
        border: none;
        border-radius: 8px;
        font-size: 1rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.2s ease;
        width: 100%;
        margin-top: 10px;
    }

    .btn-primary {
        background-color: #3182ce;
        color: white;
    }

    .btn-primary:hover:not(:disabled) {
        background-color: #2b6cb0;
        transform: translateY(-1px);
    }

    .btn:disabled {
        background-color: #a0aec0;
        cursor: not-allowed;
    }

    .alert {
        margin-top: 20px;
        padding: 12px 16px;
        border-radius: 8px;
        font-size: 0.9rem;
        text-align: center;
    }

    .alert-error {
        background-color: #fff5f5;
        color: #c53030;
        border: 1px solid #feb2b2;
    }

    .footer-info {
        text-align: center;
        margin-top: 20px;
        font-size: 0.85rem;
        color: #a0aec0;
    }
</style>