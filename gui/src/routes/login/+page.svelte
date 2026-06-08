<script>
    import { onMount } from 'svelte';

    const registeredLoginKey = 'stegocloud_registered_login';

    let email = $state('admin@gmail.com');
    let password = $state('admin');
    let errorMessage = $state('');
    let infoMessage = $state('');
    let isLoggingIn = $state(false);

    const demoUsers = [
        ['admin@gmail.com', 'admin'],
        ['free@gmail.com', 'free'],
        ['standard@gmail.com', 'standard'],
        ['pro@gmail.com', 'pro'],
        ['lowbalance@gmail.com', 'lowbalance']
    ];

    onMount(() => {
        const registeredLogin = sessionStorage.getItem(registeredLoginKey);
        if (!registeredLogin) return;

        sessionStorage.removeItem(registeredLoginKey);
        try {
            const credentials = JSON.parse(registeredLogin);
            email = credentials.email || email;
            password = credentials.password || '';
            infoMessage = 'Account created. You can log in now.';
        } catch {
            infoMessage = 'Account created. You can log in now.';
        }
    });

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
                errorMessage = errorText || 'Invalid email or password.';
            }
        } catch {
            errorMessage = 'Could not connect to auth-server.';
        } finally {
            isLoggingIn = false;
        }
    }

    function useDemo(demoEmail, demoPassword) {
        email = demoEmail;
        password = demoPassword;
        infoMessage = '';
    }
</script>

<main class="container">
    <section class="login-card">
        <header>
            <h1>StegoCloud Login</h1>
            <p>Choose a demo account to test subscription limits.</p>
        </header>

        <form onsubmit={handleLogin}>
            <label>
                <span>Email</span>
                <input type="email" bind:value={email} required placeholder="user@example.com" />
            </label>

            <label>
                <span>Password</span>
                <input type="password" bind:value={password} required placeholder="Password" />
            </label>

            <button type="submit" disabled={isLoggingIn}>
                {isLoggingIn ? 'Logging in...' : 'Log in'}
            </button>
        </form>

        <a class="register-link" href="/register">Create regular account</a>

        {#if infoMessage}
            <div class="success">{infoMessage}</div>
        {/if}

        {#if errorMessage}
            <div class="error">{errorMessage}</div>
        {/if}

        <section class="demo-section" aria-label="Demo accounts">
            <div class="demo-divider">
                <span>Demo accounts</span>
            </div>

            <div class="demo-list">
                {#each demoUsers as [demoEmail, demoPassword]}
                    <button type="button" onclick={() => useDemo(demoEmail, demoPassword)}>
                        {demoEmail.replace('@gmail.com', '')}
                    </button>
                {/each}
            </div>
        </section>
    </section>
</main>

<style>
    :global(body) {
        margin: 0;
        background: #f3f5f7;
        color: #1f2933;
        font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }

    .container {
        min-height: 100vh;
        display: grid;
        place-items: center;
        padding: 20px;
    }

    .login-card {
        width: min(100%, 430px);
        background: #fff;
        border: 1px solid #d9e2ec;
        border-radius: 8px;
        padding: 32px;
        box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
    }

    header {
        margin-bottom: 24px;
        text-align: center;
    }

    h1 {
        margin: 0;
        font-size: 1.7rem;
        color: #172033;
    }

    p {
        margin: 8px 0 0;
        color: #667085;
    }

    form {
        display: grid;
        gap: 16px;
    }

    label {
        display: grid;
        gap: 7px;
        font-weight: 700;
    }

    input {
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        padding: 12px 14px;
        font-size: 1rem;
        background: #f8fafc;
    }

    input:focus {
        outline: none;
        border-color: #2563eb;
        background: #fff;
    }

    button {
        border: 0;
        border-radius: 8px;
        padding: 11px 14px;
        font-weight: 800;
        cursor: pointer;
    }

    form button {
        margin-top: 6px;
        background: #2563eb;
        color: #fff;
    }

    .register-link {
        display: block;
        margin-top: 10px;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        padding: 11px 14px;
        color: #344054;
        background: #fff;
        font-weight: 800;
        text-align: center;
        text-decoration: none;
    }

    button:disabled {
        background: #98a2b3;
        cursor: not-allowed;
    }

    .error, .success {
        margin-top: 16px;
        padding: 11px 13px;
        border-radius: 8px;
    }

    .error {
        background: #fff1f2;
        border: 1px solid #fecdd3;
        color: #be123c;
    }

    .success {
        background: #ecfdf3;
        border: 1px solid #bbf7d0;
        color: #15803d;
    }

    .demo-section {
        margin-top: 20px;
    }

    .demo-divider {
        display: grid;
        grid-template-columns: 1fr auto 1fr;
        gap: 10px;
        align-items: center;
        color: #667085;
        font-size: 0.78rem;
        font-weight: 800;
        text-transform: uppercase;
    }

    .demo-divider::before,
    .demo-divider::after {
        content: "";
        height: 1px;
        background: #d9e2ec;
    }

    .demo-list {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 8px;
        margin-top: 14px;
    }

    .demo-list button {
        background: #f8fafc;
        border: 1px solid #cbd5e1;
        color: #344054;
    }
</style>
