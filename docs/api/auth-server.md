# Auth Server API

The auth-server is the central identity provider for the StegoCloud platform. It handles user registration, authentication, and JWT token validation. Every other microservice relies on this service to verify bearer tokens.

- **Host port:** `http://localhost:8081`
- **Base path:** `/auth`
- **Auth:** All three endpoints are **public** (no authentication required). The service does not expose any actuator endpoints.
- **Swagger UI:** `http://localhost:8081/swagger-ui/index.html`

Source files referenced: `controller/AuthController.java`, `dto/*.java`, `service/JwtService.java`, `security/SecurityConfig.java`, `exception/GlobalExceptionHandler.java`.

## Endpoints summary

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/auth/register` | Public | Create a new user account |
| POST | `/auth/login` | Public | Authenticate and receive a JWT |
| POST | `/auth/validate` | Public | Verify a JWT token's validity |

---

## Authentication model

The auth-server issues JSON Web Tokens (JWT) signed with HMAC-SHA (via the jjwt library). Every token contains the following claims:

| Claim | Type | Description |
|-------|------|-------------|
| `sub` | string | Username |
| `userId` | number (int64) | User's internal database ID |
| `role` | string | `"USER"` or `"ADMIN"` |
| `iat` | number | Issued-at timestamp (epoch seconds) |
| `exp` | number | Expiration timestamp (epoch seconds); 24 hours from issue |

The signing secret is configured via `app.jwt.secret` (Spring property), supplied at runtime through the `JWT_SECRET_KEY` environment variable. Passwords are hashed with BCrypt cost **12** (`SecurityConfig.java:37`).

### How other services use the JWT

All protected services require the JWT as a **Bearer token** in the `Authorization` header:

```
Authorization: Bearer <JWT>
```

Services **do not verify the signature locally**. Instead they forward the token to auth-server's `/auth/validate` endpoint, which returns a bare boolean `true` or `false`. This is implemented via:

- **subscription-service & ai-service** — Feign `AuthClient` calling `POST /auth/validate?token=...`
- **watermark-service (Python)** — `httpx` POST to the same URL in `app/auth.py`

Role checks: only watermark-service enforces ADMIN (owner-or-admin logic). subscription-service and ai-service require only a valid token (authentication, no role gate).

---

## POST /auth/register

Creates a new user account. Registered users always receive the `USER` role.

**Auth:** Public
**Content-Type:** `application/json`

### Request body — `RegisterRequest`

| Field | Type | Required | Notes / Validation |
|-------|------|----------|-------------------|
| `username` | string | yes | `@NotBlank`, `@Size(min=3, max=50)` |
| `email` | string | yes | `@NotBlank`, `@Email` |
| `password` | string | yes | `@NotBlank` |

Any unknown JSON property in the request body is rejected (`@JsonAnySetter` → `UnknownRegistrationPropertyException`). The body is parsed strictly — a single extra field causes a 400 error.

### Responses

| Code | Content-Type | Body |
|------|-------------|------|
| **201 Created** | `application/json` | `RegisterResponse` (see below) |
| **400 Bad Request** | `application/json` | Field-validation error map |
| **400 Bad Request** | `text/plain` | Malformed JSON or unknown property |
| **409 Conflict** | `application/json` | Duplicate-field error map |

**201 Created** — `RegisterResponse`:

```json
{
  "id": 42,
  "username": "jdoe",
  "email": "jdoe@example.com",
  "role": "USER"
}
```

Fields: `id` (int64), `username` (string), `email` (string), `role` (`"USER"` or `"ADMIN"`). New users always get `"USER"`.

### Errors

- **400** — validation failure: JSON map keyed by field name with the validation message.
  ```json
  { "username": "size must be between 3 and 50" }
  ```
- **400** — malformed JSON (parse failure): plain text `"Invalid JSON format or unknown properties in request."`
- **400** — unknown property: plain text message from `UnknownRegistrationPropertyException`, e.g. `"Unknown property: extraField"`
- **409** — duplicate email or username: JSON map with the conflicting field and a descriptive message.
  ```json
  { "email": "Email already in use" }
  ```

### Example

```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "jdoe",
    "email": "jdoe@example.com",
    "password": "secret123"
  }'
```

<details>
<summary>GUI same-origin alternative</summary>
When behind the nginx reverse proxy (port 5173), the same-origin path is `/auth/register`:

```bash
curl -X POST http://localhost:5173/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "jdoe",
    "email": "jdoe@example.com",
    "password": "secret123"
  }'
```
</details>

---

## POST /auth/login

Authenticates a user by email and password, returning a JWT token on success.

**Auth:** Public
**Content-Type:** `application/json`

### Request body — `LoginRequest`

| Field | Type | Required | Notes / Validation |
|-------|------|----------|-------------------|
| `email` | string | yes | `@NotBlank` |
| `password` | string | yes | `@NotBlank` |

### Responses

| Code | Content-Type | Body |
|------|-------------|------|
| **200 OK** | `application/json` | `LoginResponse` with JWT token |
| **400 Bad Request** | `application/json` | Field-validation error map |
| **401 Unauthorized** | `text/plain` | Error description |

**200 OK** — `LoginResponse`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqZG9lIiwidXNlcklkIjo0Miwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MTg3NzQ0MDAsImV4cCI6MTcxODg2MDgwMH0.example"
}
```

### Errors

- **400** — validation failure: same shape as register (field → message map).
- **401** — wrong password: plain text `"Invalid password"`.
- **401** — email not found: plain text `"User not found"`.
- **401** — other credential errors: plain text `"Invalid email or password."` (from the `@ExceptionHandler` catching `BadCredentialsException` and `EmailNotFoundException`).

Use the token in subsequent protected calls:

```
Authorization: Bearer <token>
```

### Example

```bash
# Capture the token into a shell variable
TOKEN=$(curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jdoe@example.com",
    "password": "secret123"
  }' | jq -r '.token')

echo "$TOKEN"
```

<details>
<summary>GUI same-origin alternative</summary>

```bash
TOKEN=$(curl -s -X POST http://localhost:5173/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jdoe@example.com",
    "password": "secret123"
  }' | jq -r '.token')
```
</details>

---

## POST /auth/validate?token=\<jwt\>

Validates a JWT token's signature and expiration. Used by other services to verify bearer tokens without sharing the signing secret.

**Auth:** Public
**Content-Type:** `application/x-www-form-urlencoded` (query parameter)

### Request parameters

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `token` | string | yes | The JWT string to validate |

### Responses

| Code | Content-Type | Body |
|------|-------------|------|
| **200 OK** | `application/json` | `true` or `false` |

**200 OK** — the endpoint returns a bare boolean. It never throws on a bad token; parsing exceptions are caught and converted to `false`.

Valid token:
```json
true
```

Expired or tampered token:
```json
false
```

### Example

```bash
# Validate the token captured from login
curl -s -X POST "http://localhost:8081/auth/validate?token=$TOKEN"
# → true

# A garbage token
curl -s -X POST "http://localhost:8081/auth/validate?token=invalid.jwt.string"
# → false
```

<details>
<summary>GUI same-origin alternative</summary>

```bash
curl -s -X POST "http://localhost:5173/auth/validate?token=$TOKEN"
```
</details>

---

## Demo accounts

These accounts are seeded by Flyway and available for testing:

| Login | Password | Role | Plan |
|-------|----------|------|------|
| admin@gmail.com | admin | ADMIN | PRO |
| free@gmail.com | free | USER | FREE |
| standard@gmail.com | standard | USER | STANDARD |
| pro@gmail.com | pro | USER | PRO |
| lowbalance@gmail.com | lowbalance | USER | FREE (low balance) |

---

## Related documentation

- [API Index](./README.md)
- [Combined OpenAPI spec](../../stegocloud-openapi.json)
- [Combined API docs](../../api-docs.html)
- [Subscription Service](./subscription-service.md) — consumes auth tokens
- [AI Service](./ai-service.md) — consumes auth tokens
- [Watermark Service](./watermark-service.md) — consumes auth tokens, enforces ADMIN role
- [Infrastructure](./infrastructure.md) — Eureka, Config Server
