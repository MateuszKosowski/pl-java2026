# Infrastructure API

This document covers the StegoCloud infrastructure services — Eureka service discovery (port 8761), Spring Cloud Config Server (port 8888), actuator/health endpoints per service, and the service-registration wiring visible to operators. All infrastructure endpoints are **internal** (not proxied through the GUI nginx).

- **Eureka dashboard**: `http://localhost:8761/` (HTML, HTTP Basic Auth)
- **Eureka registry REST**: `http://localhost:8761/eureka/apps`
- **Config Server**: `http://localhost:8888/<application>/<profile>`
- **Combined OpenAPI**: `../../stegocloud-openapi.json`

---

## Endpoints summary

| Service | Method | Path | Auth | Notes |
|---|---|---|---|---|
| eureka-server | GET | `/` | HTTP Basic | Web dashboard, HTML |
| eureka-server | GET | `/eureka/apps` | HTTP Basic | Registry dump, JSON with `Accept: application/json` |
| config-server | GET | `/{application}/{profile}` | HTTP Basic | Fetch configuration |
| config-server | GET | `/{application}/{profile}/{label}` | HTTP Basic | Fetch config at specific git label |
| subscription-service | GET | `/actuator/health` | Public | Spring Boot Actuator health |
| watermark-service | GET | `/health` | Public | FastAPI health |

---

## Eureka Server (port 8761)

The Eureka server provides service discovery for the Java services (auth-server, subscription-service, ai-service) and the Python watermark-service. It runs on port 8761 with HTTP Basic Authentication on **all** routes; the `/eureka/**` path is CSRF-exempted to allow programmatic registration (`eureka-server/.../config/SecurityConfig.java:16`).

### Auth

**HTTP Basic Authentication** on every route. Credentials set via compose environment variables:

- `EUREKA_USER` → `SPRING_SECURITY_USER_NAME`
- `EUREKA_PASSWORD` → `SPRING_SECURITY_USER_PASSWORD`

(`docker-compose.yml:52-53`)

### GET / (Web Dashboard)

- **Summary**: Eureka web dashboard — HTML page listing registered instances, status and health links.
- **Auth**: HTTP Basic (any valid EUREKA_USER/EUREKA_PASSWORD).
- **Response**: HTML.

### GET /eureka/apps (Registry REST API)

- **Summary**: Returns the full registry as XML (default) or JSON (with `Accept: application/json`).
- **Auth**: HTTP Basic.
- **Request headers**: `Accept: application/json` for JSON output.
- **Response 200 OK**: XML or JSON registry dump with all registered application instances.

#### Example

```bash
curl -u "$EUREKA_USER:$EUREKA_PASSWORD" \
  -H "Accept: application/json" \
  http://localhost:8761/eureka/apps
```

### Service registration

Services register with Eureka via the `EUREKA_URL` environment variable, which embeds credentials in the URL:

```
http://<EUREKA_USER>:<EUREKA_PASSWORD>@eureka-server:8761/eureka/
```

Logical application names registered by each service (`docker-compose.yml`):

| Service | Eureka name | Env variable |
|---|---|---|
| auth-server | `AUTH-SERVER` | `EUREKA_URL` + `SPRING_APPLICATION_NAME` |
| subscription-service | `SUBSCRIPTION-SERVICE` | `EUREKA_URL` |
| ai-service | `AI-SERVICE` | `EUREKA_URL` |
| watermark-service | `WATERMARK-SERVICE` | `EUREKA_URL` |

Eureka server itself does **not** self-register or fetch from the registry: `EUREKA_CLIENT_REGISTERWITHEUREKA=false`, `EUREKA_CLIENT_FETCHREGISTRY=false` (`docker-compose.yml:56-57`).

---

## Config Server (port 8888)

Spring Cloud Config Server backed by an external Git repository, with a native fallback for `ai-service`. Runs on port 8888.

### Auth

**HTTP Basic Authentication** on every request (`config-server/.../config/SecurityConfig.java:16-18`). Credentials from compose environment variables:

- `CONFIG_SERVER_USER` (default `admin`)
- `CONFIG_SERVER_PASSWORD` (default `admin`)

(`config-server/src/main/resources/application.yaml:9-11`)

### Git backend

- **URI**: `https://github.com/bkolacinski/pl-java2026-config.git` (`application.yaml:17`)
- **Default label**: `main` (`application.yaml:18`)
- **Native fallback**: `classpath:/config/` — holds only `ai-service.yaml` (`application.yaml:19-20`); `ai-service.yaml` configures `server.port: 8084`, Eureka client default zone, and AI model path (`config/ai-service.yaml`).

### Endpoints

### GET /{application}/{profile}

- **Summary**: Fetch configuration for a given application and profile. Merges Git-backed config with the native fallback (if applicable).
- **Auth**: HTTP Basic.
- **Path params**:
  - `application` (string, required) — e.g. `auth-server`, `subscription-service`, `ai-service`.
  - `profile` (string, required) — e.g. `default`, `dev`, `prod`.
- **Response 200 OK**: JSON with `{ name, profiles, label, version, propertySources[] }`.

### GET /{application}/{profile}/{label}

- **Summary**: Fetch configuration pinned to a specific Git label (branch, tag, or commit SHA).
- **Auth**: HTTP Basic.
- **Path params**:
  - `application` (string, required)
  - `profile` (string, required)
  - `label` (string, required) — Git branch, tag, or commit id.
- **Response 200 OK**: Same shape as above.

#### Example

```bash
curl -u "$CONFIG_SERVER_USER:$CONFIG_SERVER_PASSWORD" \
  http://localhost:8888/auth-server/default
```

```bash
curl -u "$CONFIG_SERVER_USER:$CONFIG_SERVER_PASSWORD" \
  http://localhost:8888/subscription-service/default/main
```

### Health check (compose)

The compose file healthchecks the config server by curling `http://localhost:8888/auth-server/default` with HTTP Basic credentials (`docker-compose.yml:29-38`). This is **not** an Actuator endpoint — it uses the standard config query, which succeeds with a 200 if the server is healthy and can serve config.

---

## Actuator / Health endpoints (per service)

| Service | Endpoint | Auth | Framework | Notes |
|---|---|---|---|---|
| auth-server | — | — | — | No actuator dependency |
| ai-service | — | — | — | No actuator dependency |
| eureka-server | — | — | — | No actuator dependency |
| config-server | — | — | — | No actuator dependency |
| subscription-service | `GET /actuator/health` | Public | Spring Boot Actuator | Also `/actuator/health/**` permitted. Other actuator paths require auth. Exposure configured in external config repo; Spring defaults expose `health` (+ `info`). |
| watermark-service | `GET /health` | Public | FastAPI | Registered on app root, **not** under the `/api/watermark` prefix (`app/main.py:46`). Returns `{ "status": "UP" }` on 200. |

### subscription-service

```bash
# Public — no bearer token required
curl http://localhost:8085/actuator/health
```

Response:
```json
{
  "status": "UP"
}
```

### watermark-service

```bash
# Public — no bearer token required
curl http://localhost:8082/health
```

Response:
```json
{
  "status": "UP"
}
```

---

## Service dependencies and startup order (docker-compose.yml)

1. **postgres-db** — database for auth-server, subscription-service.
2. **config-server** — must be healthy before any Spring service can start (they import config via `SPRING_CONFIG_IMPORT`). Healthcheck curls `/auth-server/default`.
3. **eureka-server** — depends on config-server; starts after config-server is healthy. Does not wait for a health endpoint.
4. **auth-server** — depends on config-server (healthy), eureka-server (started), postgres-db (started).
5. **subscription-service** — same dependencies as auth-server.
6. **ai-service** — depends on config-server (healthy), eureka-server (started).
7. **watermark-service** — Python/FastAPI, depends on config-server, eureka-server, auth-server, ai-service, subscription-service (all started).
8. **gui** — nginx, depends on auth-server, watermark-service, subscription-service.

---

## Cross-links

- [API Reference Index](./README.md)
- [Combined OpenAPI spec (`stegocloud-openapi.json`)](../../stegocloud-openapi.json)
- [Auth Server API](./auth-server.md)
- [Subscription Service API](./subscription-service.md)
- [AI Service API](./ai-service.md)
- [Watermark Service API](./watermark-service.md)
