# Subscription-Service API

The subscription-service manages the StegoCloud token economy — user plans, token balances, mock payments (upgrade/downgrade simulation), and token reservation for paid watermark operations. Runs on port **8085** with base paths `/api/subscriptions/`, `/api/payments/`, and `/api/tokens/`. All endpoints except public health require a Bearer JWT; the `JwtFilter` validates the token via auth-server and sets the principal to the JWT `userId`.

Swagger UI: `http://localhost:8085/swagger-ui/index.html`

─

## Token economy & plans

### TokenOperation enum

Defined in `domain/token/TokenOperation.java`.

| Operation | Cost |
|----------:|:-----|
| CAPACITY_CHECK | 0 |
| DETECT | 1 |
| EXTRACT | 2 |
| VISUALIZE | 3 |
| EMBED_768 | 5 |
| EMBED_1024 | 8 |
| AI_CLASSIFICATION | 2 |

### PlanCode enum

`FREE`, `STANDARD`, `PRO`.

| Plan | Monthly tokens | Allowed operations |
|:-----|:--------------|:-------------------|
| FREE | 50 | CAPACITY_CHECK, DETECT, EMBED_768 |
| STANDARD | 500 | CAPACITY_CHECK, DETECT, EMBED_768, EXTRACT, VISUALIZE, EMBED_1024 |
| PRO | 2500 | all, incl. AI_CLASSIFICATION |

### Upgrade / transition rules

- Allowed transitions: **FREE → STANDARD**, **FREE → PRO**, **STANDARD → PRO**.
- Downgrade or re-purchasing the currently active plan is **rejected** (`IllegalArgumentException` → HTTP 500 — no `@RestControllerAdvice`).
- A paid plan is valid for **one month** from purchase/upgrade.
- Upgrading starts a **new month** and **adds** the new plan's full monthly token pool to the current balance (tokens are additive).
- On expiry the plan reverts to **FREE** and the balance resets to 50.

### Reservation lifecycle

Statuses defined in `TokenReservationStatus`: `RESERVED`, `CONSUMED`, `RELEASED`.

1. **RESERVED** — tokens are temporarily held (15-minute TTL per `TokenReservationPolicy`).
2. **CONSUMED** — operation completed; tokens deducted permanently.
3. **RELEASED** — operation failed or aborted; tokens returned to balance.

### Payment session Status enum

`PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`.

─

## Endpoints summary

| Method | Path | Auth | Notes |
|:-------|:-----|:-----|:------|
| POST | `/api/payments/mock/sessions` | Bearer JWT | Create payment session (upgrade-only) |
| POST | `/api/payments/mock/sessions/{sessionId}/succeed` | Bearer JWT | Finalize as succeeded |
| POST | `/api/payments/mock/sessions/{sessionId}/fail` | Bearer JWT | Finalize as failed |
| POST | `/api/payments/mock/sessions/{sessionId}/cancel` | Bearer JWT | Finalize as cancelled |
| GET | `/api/subscriptions/status` | **PUBLIC** | Service health |
| GET | `/api/subscriptions/plans` | Bearer JWT | List available plans |
| GET | `/api/subscriptions/me` | Bearer JWT | Current subscription for caller |
| GET | `/api/subscriptions/me/tokens` | Bearer JWT | Token balance for caller |
| POST | `/api/tokens/reservations` | Bearer JWT | Reserve tokens (⚠️ see KNOWN ISSUE) |
| POST | `/api/tokens/reservations/{reservationId}/consume` | Bearer JWT | Consume (deduct) reserved tokens |
| POST | `/api/tokens/reservations/{reservationId}/release` | Bearer JWT | Release (return) reserved tokens |

> The GUI nginx reverse proxy forwards `/api/subscriptions/`, `/api/payments/`, and `/api/tokens/` to subscription-service:8085. Note: `/api/subscriptions` (no trailing slash) falls through to the `/api/` catch‑all (watermark-service).

─

## Mock payments

All payment endpoints require a valid Bearer JWT. The caller identity is resolved from the JWT principal via `UserIdentityResolver`.

### POST /api/payments/mock/sessions

Create a payment session for upgrading the caller's plan.

**Auth:** Bearer JWT

**Tokens:** none (no token cost — payment is a mock operation)

**Request**

`Content-Type: application/json`

| Field | Type | Required | Notes |
|:------|:-----|:---------|:------|
| `targetPlan` | string | yes | `"FREE"`, `"STANDARD"`, or `"PRO"`. Validated as an allowed upgrade from the caller's current plan. |

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `PaymentSessionResponse` |
| 401 | — | Missing/invalid bearer token (JwtFilter) |
| 500 | `application/json` | Default Spring error — domain violation (no advice) |

**200 body (`PaymentSessionResponse`)**

```json
{
  "id": "3b3c9e1a-5d7f-4a2b-9c8d-1e2f3a4b5c6d",
  "userId": "7",
  "targetPlan": "STANDARD",
  "status": "PENDING"
}
```

**Errors**

- **401** — bearer token missing or invalid (JwtFilter).
- **500** — `IllegalArgumentException` if the target plan is not an upgrade (downgrade or same plan) or `IllegalStateException` if there is a pending (unfinalized) session. Surface as Spring Boot's default error JSON `{timestamp, status, error, path}` because the service lacks a `@RestControllerAdvice`.

**Example**

```bash
curl -X POST http://localhost:8085/api/payments/mock/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"targetPlan": "STANDARD"}'
```

GUI same-origin: `POST /api/payments/mock/sessions` with the same body and auth header.

─

### POST /api/payments/mock/sessions/{sessionId}/succeed

Finalize a payment session as **succeeded**. Applies the plan upgrade: sets `activeUntil = now + 1 month` and adds the plan's monthly tokens to the caller's balance.

**Auth:** Bearer JWT

**Path params**

| Field | Type | Required | Notes |
|:------|:-----|:---------|:------|
| `sessionId` | UUID | yes | The payment session identifier. |

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `PaymentSessionResponse` with `status: "SUCCEEDED"` |
| 401 | — | Missing/invalid bearer token |
| 500 | `application/json` | Domain / ownership error |

**200 body**

```json
{
  "id": "3b3c9e1a-5d7f-4a2b-9c8d-1e2f3a4b5c6d",
  "userId": "7",
  "targetPlan": "STANDARD",
  "status": "SUCCEEDED"
}
```

**Properties**
- **Idempotent**: repeating with the same session returns the same response unchanged.
- **Owner-scoped**: only the user who created the session may finalize it.

**Errors**
- **500** — `"Session not found"` (unknown sessionId), `"Payment session belongs to another user"`, `"Session already completed with different outcome"`. All surface as default Spring error JSON.

**Example**

```bash
curl -X POST "http://localhost:8085/api/payments/mock/sessions/3b3c9e1a-5d7f-4a2b-9c8d-1e2f3a4b5c6d/succeed" \
  -H "Authorization: Bearer $TOKEN"
```

─

### POST /api/payments/mock/sessions/{sessionId}/fail

Finalize a payment session as **failed**. No subscription change occurs.

**Auth:** Bearer JWT

**Path params**

Same as `succeed`.

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `PaymentSessionResponse` with `status: "FAILED"` |

**200 body**

```json
{
  "id": "3b3c9e1a-5d7f-4a2b-9c8d-1e2f3a4b5c6d",
  "userId": "7",
  "targetPlan": "STANDARD",
  "status": "FAILED"
}
```

Idempotent and owner-scoped.

─

### POST /api/payments/mock/sessions/{sessionId}/cancel

Finalize a payment session as **cancelled**. No subscription change occurs.

**Auth:** Bearer JWT

**Path params**

Same as `succeed`.

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `PaymentSessionResponse` with `status: "CANCELLED"` |

**200 body**

```json
{
  "id": "3b3c9e1a-5d7f-4a2b-9c8d-1e2f3a4b5c6d",
  "userId": "7",
  "targetPlan": "STANDARD",
  "status": "CANCELLED"
}
```

Idempotent and owner-scoped.

─

## Subscription query

Endpoints for querying plans and the caller's subscription state.

### GET /api/subscriptions/status

**PUBLIC** — no JWT required. Returns the service health status.

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `ServiceStatus` |

**200 body (`ServiceStatus`)**

```json
{
  "service": "subscription-service",
  "status": "UP"
}
```

The service also exposes a standard Spring Boot Actuator health endpoint at `/actuator/health` (public).

**Example**

```bash
curl http://localhost:8085/api/subscriptions/status
```

GUI same-origin: `GET /api/subscriptions/status`.

─

### GET /api/subscriptions/plans

List all available subscription plans with their monthly token allowance and permitted operations.

**Auth:** Bearer JWT

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | Array of `PlanView` |
| 401 | — | Missing/invalid bearer token |

**200 body (`PlanView[]`)**

```json
[
  {
    "code": "FREE",
    "monthlyTokens": 50,
    "allowedOperations": ["CAPACITY_CHECK", "DETECT", "EMBED_768"]
  },
  {
    "code": "STANDARD",
    "monthlyTokens": 500,
    "allowedOperations": ["CAPACITY_CHECK", "DETECT", "EMBED_768", "EXTRACT", "VISUALIZE", "EMBED_1024"]
  },
  {
    "code": "PRO",
    "monthlyTokens": 2500,
    "allowedOperations": ["CAPACITY_CHECK", "DETECT", "EMBED_768", "EXTRACT", "VISUALIZE", "EMBED_1024", "AI_CLASSIFICATION"]
  }
]
```

**Example**

```bash
curl http://localhost:8085/api/subscriptions/plans \
  -H "Authorization: Bearer $TOKEN"
```

GUI same-origin: `GET /api/subscriptions/plans`.

─

### GET /api/subscriptions/me

Return the caller's current active subscription. A user with no subscription is auto-initialized to FREE.

**Auth:** Bearer JWT

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `CurrentSubscriptionView` |
| 401 | — | Missing/invalid bearer token |

**200 body (`CurrentSubscriptionView`)**

```json
{
  "userId": "7",
  "planCode": "STANDARD",
  "activeFrom": "2026-06-18T10:00:00Z",
  "activeUntil": "2026-07-18T10:00:00Z"
}
```

Fields: `userId` (string), `planCode` (PlanCode enum), `activeFrom` (ISO-8601 date-time), `activeUntil` (ISO-8601 date-time).

**Example**

```bash
curl http://localhost:8085/api/subscriptions/me \
  -H "Authorization: Bearer $TOKEN"
```

GUI same-origin: `GET /api/subscriptions/me`.

─

### GET /api/subscriptions/me/tokens

Return the caller's current token balance — both available (spendable) and reserved (held by active reservations).

**Auth:** Bearer JWT

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `TokenBalanceView` |
| 401 | — | Missing/invalid bearer token |

**200 body (`TokenBalanceView`)**

```json
{
  "userId": "7",
  "availableTokens": 450,
  "reservedTokens": 8
}
```

Fields: `userId` (string), `availableTokens` (int), `reservedTokens` (int).

**Example**

```bash
curl http://localhost:8085/api/subscriptions/me/tokens \
  -H "Authorization: Bearer $TOKEN"
```

GUI same-origin: `GET /api/subscriptions/me/tokens`.

─

## Token reservation

> **⚠️ KNOWN ISSUE — endpoints not currently wired.**  
> `controller/TokenReservationController.java` declares the handler methods and an `@Tag`, but the class is **missing** `@RestController` and a class-level `@RequestMapping`. As written it is not registered as a Spring MVC handler, so the paths below return **404**. This breaks every paid watermark operation, which calls these endpoints via `watermark-service-py/app/subscription_client.py`. The GUI nginx already proxies `/api/tokens/` to this service and `subscription_client.py` posts to `/api/tokens/reservations`, so the intended base path is unambiguous.  
> **Fix:** add `@RestController` and `@RequestMapping("/api/tokens/reservations")` to the class.  
> *(Source: `subscription-service/src/main/java/pl/zzpj/subscription_service/controller/TokenReservationController.java:27-28`)*

The endpoints below document the **intended contract** once the fix is applied. All require a valid Bearer JWT.

### POST /api/tokens/reservations

Reserve tokens for an operation. The service evaluates the caller's plan and balance and either reserves the tokens (201) or returns a structured rejection.

**Auth:** Bearer JWT

**Tokens:** none (reservation itself has no token cost)

**Request**

`Content-Type: application/json`

| Field | Type | Required | Notes |
|:------|:-----|:---------|:------|
| `operation` | string | yes | A `TokenOperation` value: `CAPACITY_CHECK`, `DETECT`, `EXTRACT`, `VISUALIZE`, `EMBED_768`, `EMBED_1024`, `AI_CLASSIFICATION` |
| `externalOperationId` | string | no | Optional caller-assigned id for correlation |

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 201 | `application/json` | `TokenReservationResponse` with `status: "RESERVED"` |
| 403 | `application/json` | `TokenReservationErrorResponse` — operation not allowed |
| 409 | `application/json` | `TokenReservationErrorResponse` — insufficient tokens, plan not found, or subscription expired |
| 401 | — | Missing/invalid bearer token |

**201 body (`TokenReservationResponse`)**

```json
{
  "reservationId": "d4e5f6a7-b8c9-4d0e-f123-4567890abcde",
  "userId": "7",
  "operation": "EMBED_768",
  "tokens": 5,
  "status": "RESERVED",
  "expiresAt": "2026-06-18T11:00:00Z"
}
```

**Error body (`TokenReservationErrorResponse`)**

```json
{
  "code": "INSUFFICIENT_TOKENS",
  "message": "Not enough available tokens to reserve"
}
```

**Error codes**

| HTTP | `code` | Trigger |
|:----|:-------|:--------|
| 409 | `INSUFFICIENT_TOKENS` | Available balance is too low |
| 403 | `OPERATION_NOT_ALLOWED` | User's plan does not permit this operation |
| 409 | `PLAN_NOT_FOUND` | Plan configuration missing from catalog |
| 409 | `SUBSCRIPTION_EXPIRED` | Subscription has expired (reverts to FREE) |

**Example**

```bash
curl -X POST http://localhost:8085/api/tokens/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"operation": "EMBED_768"}'
```

─

### POST /api/tokens/reservations/{reservationId}/consume

Finalize a reservation, permanently deducting the reserved tokens from the caller's balance. Ownership-guarded — only the user who created the reservation may consume it.

**Auth:** Bearer JWT

**Path params**

| Field | Type | Required | Notes |
|:------|:-----|:---------|:------|
| `reservationId` | UUID | yes | The reservation identifier. |

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `TokenReservationResponse` with `status: "CONSUMED"` |
| 401 | — | Missing/invalid bearer token |

**200 body**

```json
{
  "reservationId": "d4e5f6a7-b8c9-4d0e-f123-4567890abcde",
  "userId": "7",
  "operation": "EMBED_768",
  "tokens": 5,
  "status": "CONSUMED",
  "expiresAt": "2026-06-18T11:00:00Z"
}
```

**Example**

```bash
curl -X POST "http://localhost:8085/api/tokens/reservations/d4e5f6a7-b8c9-4d0e-f123-4567890abcde/consume" \
  -H "Authorization: Bearer $TOKEN"
```

─

### POST /api/tokens/reservations/{reservationId}/release

Cancel a reservation, returning the reserved tokens to the caller's balance. Ownership-guarded.

**Auth:** Bearer JWT

**Path params**

| Field | Type | Required | Notes |
|:------|:-----|:---------|:------|
| `reservationId` | UUID | yes | The reservation identifier. |

**Responses**

| Code | Content-Type | Body |
|:----|:-------------|:-----|
| 200 | `application/json` | `TokenReservationResponse` with `status: "RELEASED"` |
| 401 | — | Missing/invalid bearer token |

**200 body**

```json
{
  "reservationId": "d4e5f6a7-b8c9-4d0e-f123-4567890abcde",
  "userId": "7",
  "operation": "EMBED_768",
  "tokens": 5,
  "status": "RELEASED",
  "expiresAt": "2026-06-18T11:00:00Z"
}
```

**Example (reserve → consume flow)**

```bash
# 1. Reserve tokens
RESERVATION=$(curl -s -X POST http://localhost:8085/api/tokens/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"operation": "EMBED_768"}')
ID=$(echo "$RESERVATION" | python3 -c "import sys,json; print(json.load(sys.stdin)['reservationId'])")

# 2. Consume (or release on error)
curl -X POST "http://localhost:8085/api/tokens/reservations/$ID/consume" \
  -H "Authorization: Bearer $TOKEN"

# Alternative — release instead:
# curl -X POST "http://localhost:8085/api/tokens/reservations/$ID/release" \
#   -H "Authorization: Bearer $TOKEN"
```

─

## Related docs

- [API overview](./README.md)
- [Combined OpenAPI spec](../../stegocloud-openapi.json)
- [Combined API doc HTML](../../api-docs.html)
- [Auth server API](./auth-server.md)
- [AI service API](./ai-service.md)
- [Watermark service API](./watermark-service.md)
- [Infrastructure](./infrastructure.md)
