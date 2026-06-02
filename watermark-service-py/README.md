# watermark-service (Python)

Production replacement for the Java `watermark-service`. Uses [`invisible-watermark`](https://github.com/ShieldMnt/invisible-watermark) (DwtDctSvd mode) for the actual frequency-domain embedding, with an AES-GCM crypto layer on top so the payload is **encrypted and authenticated** before it ever reaches the algorithm.

## Run (in compose)

```
docker compose up -d --build watermark-service
```

Eureka name: `WATERMARK-SERVICE`. Port: `8082`. Drop-in for the (now removed) Java service — clients (GUI, Feign) need no changes.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/watermark/embed` | JWT | multipart `image`+`text`, returns PNG body + `X-Image-Category/Label/Confidence` + `X-Max-Text-Bytes` headers |
| POST | `/api/watermark/detect` | JWT | returns `{"watermarked", "ownerIdentity", "version"}` |
| POST | `/api/watermark/extract` | JWT | returns `{"ownerIdentity", "text"}`; 403 on owner mismatch |
| POST | `/api/watermark/visualize` | JWT | returns heatmap PNG of pixel diff from sentinel embed |
| POST | `/api/watermark/capacity` | JWT | returns `{"maxTextBytes", "minImageWidth", "minImageHeight", "imageWidth", "imageHeight", "imageOk"}` |
| GET  | `/health` | none | `{"status":"UP"}` |
| GET  | `/docs` | none | Swagger UI |

## User contract: PNG in, PNG out, do not recompress

The watermark hides bits in DCT coefficients. **JPEG compression also operates on
DCT** and aggressively rounds the same coefficients the watermark lives in, so any
JPEG re-encode (even Q=95) likely destroys it; Q=50 always destroys it. Same for
screenshots, resizes, filters, or anything that passes through a social-network
re-encoder (Discord, Twitter, Instagram).

What the end-user MUST do for the watermark to survive:

- **Upload PNG** to `/embed`. JPG input is accepted (the algorithm decodes it) but
  the watermark has less headroom because the source already lost DCT detail.
- **Download the result** — `/embed` always returns `Content-Type: image/png`.
  The GUI saves it as `watermarked_image.png`.
- **Distribute that exact file** without re-saving, re-compressing, screenshotting,
  resizing, or uploading anywhere that re-encodes. Keep it PNG, byte-for-byte.

This is a fundamental limitation of the frequency-domain watermarking family; the
Java implementation has the same constraint. The use case is leak-tracing for
copies that move untouched (recipient forwards a file as-is) — not DRM against an
adversary willing to run `convert -quality 50`.

The GUI shows an orange warning on the embed page and on the result card; the
input filter on the embed tab restricts to `accept="image/png"`. Detect/Extract/
Visualize keep `image/png, image/jpeg` so users can confirm "yes this JPG copy
lost the watermark" rather than getting a silent file-picker rejection.

## Security model

- Payload is `owner|text` UTF-8, **encrypted with AES-256-GCM** before embedding.
- Encryption key is derived from `WATERMARK_APP_KEY` (server secret) via SHA-256. Without it nobody can read or forge watermarks.
- Authentication tag (GCM 128-bit) means tampered or third-party watermarks fail to decrypt and `detect` returns `watermarked=false` — effectively zero false positives.
- Magic bytes `WMPY` + version byte at envelope head allow fast format rejection and forward-compatible upgrades.
- **Reed-Solomon ECC** (16 parity bytes, corrects up to 8 byte errors) wraps the encrypted envelope so dwtDctSvd's occasional bit-flips on borderline-sized images don't fail the GCM tag.
- Wire format: `[ MAGIC(4) | VERSION(1) | NONCE(12) | CIPHERTEXT_AND_TAG(n) ] + ECC_PARITY(16)`. Overhead: **49 bytes**.

## Adaptive capacity

The service picks the largest watermark capacity an image can carry. Two tiers:

| Image min side | `length_bits` | Total bytes | Usable text (after envelope + `owner-id|`) |
|---|---|---|---|
| **≥1600 px** | 1024 | 128 | **~71 chars** |
| **≥1200 px** | 768  | 96  | **~39 chars** |
| <1200 px | — | — | rejected |

Tiers were calibrated empirically against random-pixel images (worst case for dwtDctSvd); real photographs typically work at slightly smaller dimensions but the floors above are guaranteed-safe.

- `POST /api/watermark/capacity` reports the picked tier + character budget for any uploaded image.
- `/embed` response includes `X-Max-Text-Bytes` and `X-Watermark-Length-Bits` headers.
- `/detect` tries each known length until one decrypts → no need to remember which tier was used.
- The GUI uses `/capacity` for a live byte-counter near the text input and disables submit for too-small images.

## Configuration

Configuration flows from three sources, in priority order:

1. **Process environment variables** (set by docker-compose or operator)
2. **Spring Cloud Config Server** (`http://config-server:8888/watermark-service/default` + `/application/default`)
3. **Defaults** declared in `app/config.py`

The service polls config-server at startup with exponential backoff (1s → 16s) and degrades gracefully to env+defaults if config-server is unreachable.

### Env vars

| Var | Default | Purpose |
|---|---|---|
| `CONFIG_SERVER_URL` | `http://config-server:8888` | Spring Cloud Config Server URL (set empty to disable) |
| `EUREKA_URL` | `http://eureka-server:8761/eureka/` | Eureka registry |
| `AUTH_SERVER_URL` | `http://auth-server:8081` | Token validation |
| `AI_SERVICE_URL` | `http://ai-service:8084` | Classification call from `/embed` |
| `WATERMARK_APP_KEY` | `local-dev-watermark-secret` | **REQUIRED in production.** Master secret for crypto. Rotate ⇒ all prior watermarks become unreadable. |
| `INSTANCE_HOSTNAME` | `watermark-service` | Hostname registered with Eureka |
| `LOG_LEVEL` | `INFO` | Root logger level |

## Local tests

```
cd watermark-service-py
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
pytest -v
```

`.venv/` is excluded from the Docker build context via `.dockerignore` — without it the local torch wheels (~5 GB) get copied into the image and overflow disk.

## Roll back to Java backend

```
git checkout main
docker compose up -d --build watermark-service
```
