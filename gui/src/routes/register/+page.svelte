<script>
    const registeredLoginKey = 'stegocloud_registered_login';

    let username = $state('');
    let email = $state('');
    let password = $state('');
    let errorMessage = $state('');
    let isRegistering = $state(false);

    async function handleRegister(event) {
        event.preventDefault();
        errorMessage = '';
        isRegistering = true;

        try {
            const response = await fetch('/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password })
            });

            if (response.ok) {
                sessionStorage.setItem(registeredLoginKey, JSON.stringify({ email, password }));
                window.location.href = '/login';
            } else {
                errorMessage = await readError(response);
            }
        } catch {
            errorMessage = 'Could not connect to auth-server.';
        } finally {
            isRegistering = false;
        }
    }

    async function readError(response) {
        try {
            const data = await response.json();
            if (typeof data === 'string') return data;
            if (data.email) return data.email;
            if (data.username) return data.username;
            if (data.password) return data.password;
            if (data.user) return data.user;
            if (data.error) return data.error;
        } catch {
            const text = await response.text();
            if (text) return text;
        }
        return 'Registration failed.';
    }
</script>

<main class="container">
    <section class="register-card">
        <header>
            <h1>Create Account</h1>
            <p>New accounts start as regular users on the FREE plan.</p>
        </header>

        <form onsubmit={handleRegister}>
            <label>
                <span>Username</span>
                <input type="text" bind:value={username} required minlength="3" maxlength="50" placeholder="username" />
            </label>

            <label>
                <span>Email</span>
                <input type="email" bind:value={email} required placeholder="user@example.com" />
            </label>

            <label>
                <span>Password</span>
                <input type="password" bind:value={password} required placeholder="Password" />
            </label>

            <button type="submit" disabled={isRegistering}>
                {isRegistering ? 'Creating...' : 'Create account'}
            </button>
        </form>

        {#if errorMessage}
            <div class="error">{errorMessage}</div>
        {/if}

        <a class="login-link" href="/login">Back to login</a>
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

    .register-card {
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
        margin-top: 6px;
        border: 0;
        border-radius: 8px;
        padding: 11px 14px;
        background: #2563eb;
        color: #fff;
        font-weight: 800;
        cursor: pointer;
    }

    button:disabled {
        background: #98a2b3;
        cursor: not-allowed;
    }

    .error {
        margin-top: 16px;
        padding: 11px 13px;
        border-radius: 8px;
        background: #fff1f2;
        border: 1px solid #fecdd3;
        color: #be123c;
    }

    .login-link {
        display: block;
        margin-top: 20px;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        padding: 11px 14px;
        color: #344054;
        background: #fff;
        font-weight: 800;
        text-align: center;
        text-decoration: none;
    }
</style>
