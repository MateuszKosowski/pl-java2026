# AI Service API

The AI Service (port **8084**, base path `/api`) provides image classification using a MobileNetV2 ONNX model with CategoryMapper, reducing ~1000 ImageNet labels to 13 broad categories. It is an **internal-only** service — not proxied by the GUI nginx; in normal operation it is reached only by watermark-service via `AI_SERVICE_URL` during embed operations.

- **Auth:** Bearer JWT required on every request (exceptions: Swagger/OpenAPI docs). JwtFilter validates the token via Feign `AuthClient` calling `POST /auth/validate?token=...` on auth-server. No role gate — any valid JWT passes.
- **Token cost:** `AI_CLASSIFICATION` = **2 tokens** (PRO plan only; see [subscription-service](./subscription-service.md)).
- **Swagger UI:** `http://localhost:8084/swagger-ui/index.html`
- **Combined OpenAPI spec:** `../../stegocloud-openapi.json` / `../../api-docs.html`

## Endpoints

| Method | Path               | Auth       | Notes                                    |
|--------|--------------------|------------|------------------------------------------|
| POST   | `/api/classify`    | Bearer JWT | Classify image; costs 2 tokens (PRO plan) |

---

## POST /api/classify

Classifies the content of a provided image file using the ONNX MobileNetV2 model (13 broad categories via CategoryMapper). This endpoint is called by watermark-service's embed flow when the caller's plan and token balance permit AI_CLASSIFICATION.

**Auth:** Bearer JWT (no role gate). **Token operation:** AI_CLASSIFICATION (cost: 2). **PRO plan required.**

### Request

`Content-Type: multipart/form-data`

| Field | Type          | Required | Notes                                      |
|-------|---------------|----------|--------------------------------------------|
| file  | `MultipartFile` | yes    | The image file. Accepted image formats depend on what ONNX runtime can decode. |

**Multipart limits:**
- `max-file-size`: **20MB**
- `max-request-size`: **25MB**

Larger files are rejected at the servlet container level before reaching the controller.

### Responses

| Code | Content-Type       | Body                                      |
|------|--------------------|-------------------------------------------|
| 200  | `application/json` | `ClassificationResult` (see below)        |

**200 OK — ClassificationResult**

```json
{
  "label": "golden retriever",
  "category": "dog",
  "confidence": 0.9321,
  "categoryConfidence": 0.9700,
  "top3": [
    { "label": "golden retriever",    "confidence": 0.9321 },
    { "label": "Labrador retriever",  "confidence": 0.0310 },
    { "label": "Rhodesian ridgeback", "confidence": 0.0085 }
  ]
}
```

| Field                | Type            | Description                                                 |
|----------------------|-----------------|-------------------------------------------------------------|
| `label`              | string          | The most specific ImageNet class label (e.g. "golden retriever") |
| `category`           | string          | The broad category mapped from the label (e.g. "dog")      |
| `confidence`         | double          | Probability of the top prediction in range **[0, 1]**       |
| `categoryConfidence` | double          | Aggregated confidence for the broad category in range **[0, 1]** |
| `top3`               | array of object | The three highest-scoring predictions                      |

Each `top3` entry:

| Field        | Type   | Description                                    |
|-------------|--------|------------------------------------------------|
| `label`      | string | ImageNet class label                           |
| `confidence` | double | Probability for that label in range **[0, 1]** |

### Errors

- **400 Bad Request** — `IllegalArgumentException` (e.g. validation failure in the service layer). Body: `{ "error": "<message>" }`.
- **401 Unauthorized** — missing or invalid Bearer JWT. Returned by `JwtFilter` before the endpoint is reached.
- **500 Internal Server Error** — unreadable/oversized image, ONNX runtime errors (`OrtException`), I/O errors (`IOException`), or any other unhandled exception. Body: `{ "error": "Internal server error" }`.

### Example

Classify an image using a bearer token:

```bash
curl -X POST http://localhost:8084/api/classify \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@photo.jpg"
```

> **Note:** In normal operation this endpoint is invoked by watermark-service, not directly by browsers. The GUI nginx does not proxy `/api/classify`. To test directly during development, use port 8084 and a JWT obtained from `POST /auth/login` (see [auth-server](./auth-server.md)).

---

### Implementation details

- **Controller:** `ClassificationController.java` (`ai-service/src/main/java/pl/zzpj/ai_service/ClassificationController.java`) — `@PostMapping(value = "/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`.
- **Response model:** `ClassificationResult.java` — Java record `{ label, category, confidence, categoryConfidence, top3: List<TopPrediction> }`. `TopPrediction` is a nested record `{ label, confidence }`.
- **Error handler:** `GlobalExceptionHandler.java` — `IllegalArgumentException` → 400 `{ "error": "..." }`; all other exceptions → 500 `{ "error": "Internal server error" }` with a server-side error log.
- **Security:** `SecurityConfig.java` — stateless session, JwtFilter on every request except `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error`. No role gate.
- **Model:** MobileNetV2 ONNX runtime; `CategoryMapper` reduces ~1000 ImageNet labels to 13 broad categories.
- **Multipart limits:** configured in `application.yaml` (max-file-size 20MB, max-request-size 25MB).

---

[Back to index](./README.md) &bull; [Combined OpenAPI spec](../../stegocloud-openapi.json) &bull; [HTML docs](../../api-docs.html) &bull; [auth-server](./auth-server.md) &bull; [subscription-service](./subscription-service.md) &bull; [watermark-service](./watermark-service.md) &bull; [infrastructure](./infrastructure.md)
