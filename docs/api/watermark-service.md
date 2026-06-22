# Watermark Service API

The watermark service is a Python/FastAPI application (port 8082) that provides steganographic watermarking for PNG images. It can embed hidden text payloads into PNG pixel data, detect the presence of watermarks, extract the hidden text (owner-gated), visualize watermark distribution as a heatmap, and report an image's embedding capacity. All functional endpoints under `/api/watermark/*` require a Bearer JWT; `/health` is public. Swagger UI: `http://localhost:8082/docs` | OpenAPI: `http://localhost:8082/openapi.json`.

Source files: `watermark-service-py/app/routes.py`, `app/auth.py`, `app/main.py`, `app/subscription_client.py`, `app/watermark.py`, `app/crypto.py`.

## Endpoints summary

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/watermark/embed` | Bearer JWT | Tokens EMBED_768 (5) or EMBED_1024 (8) + optional AI_CLASSIFICATION (2); returns binary PNG |
| POST | `/api/watermark/detect` | Bearer JWT | Token DETECT (1); returns JSON |
| POST | `/api/watermark/extract` | Bearer JWT | Token EXTRACT (2); OWNER-or-ADMIN role gate |
| POST | `/api/watermark/visualize` | Bearer JWT | Token VISUALIZE (3); returns binary PNG heatmap |
| POST | `/api/watermark/capacity` | Bearer JWT | FREE (CAPACITY_CHECK = 0, no token reservation); returns JSON |
| GET | `/health` | public | `{ "status": "UP" }` |

## Auth & token flow

**Authentication:** Every `/api/watermark/*` endpoint requires `Authorization: Bearer <JWT>` via the `require_principal` dependency (`app/auth.py:63-95`). The service does NOT verify the JWT signature locally — it calls auth-server `POST /auth/validate?token=<jwt>` and requires the literal JSON response `true`. The principal is derived from the JWT body as `{sub}-{userId}` (e.g. `alice-7`), sanitised with `^[A-Za-z0-9._@-]{1,64}$` — fallback `"User"`.

- **401** `{ "detail": { "error": "Invalid or expired token" } }` — missing, malformed, or invalid token.
- **503** `{ "detail": { "error": "Authentication service is currently unavailable" } }` — auth-server unreachable.

**Token economy:** Paid operations (embed, detect, extract, visualize) call subscription-service to reserve tokens **before** doing work, then **consume** on success or **release** on error (`app/subscription_client.py`). Capacity check reserves nothing (CAPACITY_CHECK cost = 0). Reservation errors propagate to the caller:

| Reservation error | Status | Body shape |
|------------------|--------|------------|
| Insufficient tokens | 409 | `{ "code": "INSUFFICIENT_TOKENS", "message": "..." }` |
| Operation not allowed | 403 | `{ "code": "OPERATION_NOT_ALLOWED", "message": "..." }` |
| Plan not found | 409 | `{ "code": "PLAN_NOT_FOUND", "message": "..." }` |
| Subscription expired | 409 | `{ "code": "SUBSCRIPTION_EXPIRED", "message": "..." }` |
| Invalid auth (passthrough) | 401 | `detail` string |
| Subscription service down | 503 | `{ "detail": "Subscription service is currently unavailable" }` |

**KNOWN ISSUE** — `TokenReservationController` in subscription-service is missing `@RestController` and class-level `@RequestMapping`, so `/api/tokens/reservations/*` returns **404**. This breaks every paid watermark operation. The intended contract is documented in [`./subscription-service.md`](./subscription-service.md). Until the Java class is wired, all paid endpoints will fail with an upstream 404.

**Image & text limits:**

| Limit | Value |
|-------|-------|
| Max image size | 25 MB (`_MAX_IMAGE_BYTES`) |
| Max text payload | 4096 bytes (`_MAX_TEXT_BYTES`) |
| Accepted format | PNG only (embed checks `\\x89PNG` magic) |
| Min image (smallest tier) | 1024×1024 (`TIERS`) |

**Tier selection** (`app/watermark.py:105-117`): images exceeding FHD pixel count (1920×1080 = 2,073,600 px) qualify for the 1024-bit tier (token: EMBED_1024, cost 8); images between 1024×1024 and FHD use the 768-bit tier (EMBED_768, cost 5). Images below 1024×1024 are rejected on the embed path.

────────────────

## POST /api/watermark/embed

Embeds a text watermark into a PNG image using the caller's identity as the owner. Returns the watermarked PNG bytes directly (not JSON). Optionally runs AI classification if the plan allows.

**Auth:** Bearer JWT (required)

**Tokens:** EMBED_768 (5) or EMBED_1024 (8) selected by image pixel count vs FHD threshold; plus AI_CLASSIFICATION (2) when the plan and token balance permit (403/409 from reservation skips classification silently, returning `"unknown"` headers).

**Request:** `Content-Type: multipart/form-data`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `image` | file | yes | PNG image file. Non-PNG → 400. > 25 MB → 413. Too small → 400 with min dimensions. |
| `text` | string | yes | Watermark payload. Blank → 400. > 4096 bytes → 413. Too long for image+owner → 400. |

**Response — 200 OK:** `Content-Type: image/png`

The body is the raw PNG bytes. Response headers:

| Header | Example | Notes |
|--------|---------|-------|
| `X-Image-Category` | `"indoor_scene"` | AI category label; `"unknown"` when not run |
| `X-Image-Label` | `"library"` | AI fine-grained label |
| `X-Image-Confidence` | `"0.9321"` | AI confidence for the label |
| `X-Image-Category-Confidence` | `"0.9812"` | AI confidence for the category |
| `X-Max-Text-Bytes` | `"81"` | Max UTF-8 bytes the image+owner can carry at chosen tier |
| `X-Watermark-Length-Bits` | `"768"` | Chosen tier bit-length (768 or 1024) |

**Errors:**

| Status | Body | Trigger |
|--------|------|---------|
| 400 | `{ "detail": "text must not be blank" }` | Empty/whitespace-only text |
| 400 | `{ "detail": "Not a PNG image ..." }` | Non-PNG file (magic check) |
| 400 | `{ "detail": "Image too small (WxH). Minimum ..." }` | Below 1024×1024 |
| 400 | `{ "detail": "Text too long: max N bytes for this image and owner." }` | Text exceeds capacity |
| 413 | `{ "detail": "Text too large (max 4096 bytes)" }` | Text > 4096 UTF-8 bytes |
| 422 | `{ "detail": "..." }` | Embed verification failed at every tier (pathological image content) |
| 401/403/409/503 | see [Auth & token flow](#auth--token-flow) | Auth or reservation error |

**Example:**

```bash
# Save watermarked PNG to output.png; print response headers with -D (or -i)
curl -s -o output.png -D - \
  -H "Authorization: Bearer $TOKEN" \
  -F "image=@photo.png" \
  -F "text=Secret message" \
  http://localhost:8082/api/watermark/embed
```

The same-origin GUI path (via nginx) is `POST /api/watermark/embed` on port 5173 — the `/api/` catch-all routes to watermark-service:8082.

────────────────

## POST /api/watermark/detect

Checks whether a PNG image contains a detectable watermark and returns the owner identity if found.

**Auth:** Bearer JWT (required)

**Token:** DETECT (cost 1)

**Request:** `Content-Type: multipart/form-data`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `image` | file | yes | Image to inspect. > 25 MB → 413. |

**Response — 200 OK:** `Content-Type: application/json`

```json
{
  "watermarked": true,
  "ownerIdentity": "alice-7",
  "version": 1,
  "lengthBits": 768
}
```

| Field | Type | Notes |
|-------|------|-------|
| `watermarked` | boolean | `true` if a watermark was found |
| `ownerIdentity` | string\|null | The owner principal embedded with the watermark; `null` if not watermarked |
| `version` | int\|null | `1` when `watermarked` is true, else `null` |
| `lengthBits` | int\|null | Bit-length of the embedded watermark (768 or 1024); `null` if not watermarked |

**Errors:**

| Status | Body | Trigger |
|--------|------|---------|
| 413 | `{ "detail": "Image too large (max 26214400 bytes)" }` | Image > 25 MB |
| 401/403/409/503 | see [Auth & token flow](#auth--token-flow) | Auth or reservation error |

**Example:**

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  -F "image=@watermarked.png" \
  http://localhost:8082/api/watermark/detect | jq .
```

────────────────

## POST /api/watermark/extract

Extracts the hidden text from a watermarked PNG image. The caller must be the watermark owner OR hold an admin token (`sub=admin, userId=1` or JWT `role=ADMIN`).

**Auth:** Bearer JWT (required); OWNER or ADMIN

**Token:** EXTRACT (cost 2)

**Request:** `Content-Type: multipart/form-data`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `image` | file | yes | PNG image with an embedded watermark. > 25 MB → 413. |

**Response — 200 OK:** `Content-Type: application/json`

```json
{
  "ownerIdentity": "alice-7",
  "text": "Secret message"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `ownerIdentity` | string | The owner principal that was embedded in the watermark |
| `text` | string | The decrypted hidden payload |

**Errors:**

| Status | Body | Trigger |
|--------|------|---------|
| 400 | `{ "detail": "No watermark found in this image" }` | Image has no detectable watermark |
| 403 | `{ "detail": "Requester is not allowed to read this watermark" }` | Caller is not the owner and not admin |
| 413 | `{ "detail": "Image too large (max 26214400 bytes)" }` | Image > 25 MB |
| 401/403/409/503 | see [Auth & token flow](#auth--token-flow) | Auth or reservation error |

**Example:**

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  -F "image=@watermarked.png" \
  http://localhost:8082/api/watermark/extract | jq .
```

────────────────

## POST /api/watermark/visualize

Generates a PNG heatmap showing the watermark distribution in the supplied image. Returns raw PNG bytes.

**Auth:** Bearer JWT (required)

**Token:** VISUALIZE (cost 3)

**Request:** `Content-Type: multipart/form-data`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `image` | file | yes | PNG or other image format the watermark library can read. > 25 MB → 413. |

**Response — 200 OK:** `Content-Type: image/png`

Body is the raw PNG heatmap bytes.

**Errors:**

| Status | Body | Trigger |
|--------|------|---------|
| 400 | `{ "detail": "..." }` | Image could not be decoded or processed |
| 413 | `{ "detail": "Image too large (max 26214400 bytes)" }` | Image > 25 MB |
| 401/403/409/503 | see [Auth & token flow](#auth--token-flow) | Auth or reservation error |

**Example:**

```bash
curl -s -o heatmap.png \
  -H "Authorization: Bearer $TOKEN" \
  -F "image=@watermarked.png" \
  http://localhost:8082/api/watermark/visualize
```

────────────────

## POST /api/watermark/capacity

Calculates the maximum text embedding capacity for a given PNG image, based on its dimensions and the watermark tier it qualifies for. Does NOT reserve or consume tokens (CAPACITY_CHECK cost = 0, free).

**Auth:** Bearer JWT (required)

**Token:** FREE — no reservation performed, cost is 0.

**Request:** `Content-Type: multipart/form-data`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `image` | file | yes | Image to analyse. > 25 MB → 413. |

**Response — 200 OK:** `Content-Type: application/json`

```json
{
  "maxTextBytes": 81,
  "minImageWidth": 1024,
  "minImageHeight": 1024,
  "imageWidth": 1920,
  "imageHeight": 1080,
  "imageOk": true,
  "lengthBits": 768
}
```

| Field | Type | Notes |
|-------|------|-------|
| `maxTextBytes` | int | Maximum UTF-8 bytes that can be embedded with the caller's owner string |
| `minImageWidth` | int | Minimum required image width for the smallest tier (1024) |
| `minImageHeight` | int | Minimum required image height for the smallest tier (1024) |
| `imageWidth` | int | Actual image width (after EXIF orientation) |
| `imageHeight` | int | Actual image height (after EXIF orientation) |
| `imageOk` | bool | Whether the image meets the minimum dimension requirements |
| `lengthBits` | int | Chosen watermark bit-length (0 if image is below minimum tier; 768 or 1024 otherwise) |

**Errors:**

| Status | Body | Trigger |
|--------|------|---------|
| 413 | `{ "detail": "Image too large (max 26214400 bytes)" }` | Image > 25 MB |
| 401 | `{ "detail": { "error": "Invalid or expired token" } }` | Missing/invalid bearer token |
| 503 | `{ "detail": { "error": "Authentication service is currently unavailable" } }` | Auth-server unreachable |

**Example:**

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  -F "image=@photo.png" \
  http://localhost:8082/api/watermark/capacity | jq .
```

────────────────

## GET /health

Lightweight health check registered on the app root (not under `/api/watermark`). No auth required.

**Auth:** Public

**Response — 200 OK:** `Content-Type: application/json`

```json
{
  "status": "UP"
}
```

**Example:**

```bash
curl -s http://localhost:8082/health | jq .
```

────────────────

**Cross-references**

- [API index](./README.md)
- [Combined OpenAPI spec](../../stegocloud-openapi.json) — note: this file has known gaps for watermark responses (binary PNG schemas empty, missing error codes)
- [Combined API docs](../../api-docs.html)
- [Auth server API](./auth-server.md) — token validation dependency
- [Subscription service API](./subscription-service.md) — token reservation dependency
- [AI service API](./ai-service.md) — optional AI classification dependency during embed
- [Infrastructure notes](./infrastructure.md) — Eureka, config-server, GUI nginx routing
