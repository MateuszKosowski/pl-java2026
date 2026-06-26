# StegoCloud

StegoCloud is a microservice system for embedding, detecting and extracting encrypted watermarks in PNG images. It combines a Java/Spring Boot backend, a Python/FastAPI watermarking engine and a Svelte frontend into one Docker Compose environment.

The project focuses on a practical business flow: users have subscription plans, operations consume tokens, and watermarking requests are authorized, validated and billed before the image is processed.

## Highlights

- Microservice architecture with Spring Cloud Config and Eureka service discovery.
- JWT-based authentication and role-aware access to watermark operations.
- Subscription and token economy with reservation, consumption and release flows.
- Encrypted watermark payloads using AES-GCM before embedding.
- Python/FastAPI service for PNG watermarking, detection, extraction and visualization.
- Java 21 features, including records, sealed interfaces and pattern matching for switch.
- OpenAPI documentation for the public API surface.
- Automated tests covering Java services and the Python watermarking service.
- Docker Compose setup for local end-to-end runs.

## Tech Stack

| Area | Technologies |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security, Spring Cloud Config, Eureka, OpenFeign |
| Data | PostgreSQL, Flyway, JPA/Hibernate |
| Watermarking | Python, FastAPI, AES-GCM, Reed-Solomon ECC, invisible-watermark |
| AI | ONNX Runtime, MobileNetV2 image classification |
| Frontend | Svelte, Vite, nginx |
| Quality | JUnit 5, Cucumber, ArchUnit, Spring Cloud Contract, pytest, Checkstyle, Spotless, JaCoCo, SonarQube |
| Delivery | Docker, Docker Compose, GitHub Actions |

## Modules

| Module | Responsibility |
|---|---|
| `auth-server` | User registration, login, roles and JWT validation |
| `subscription-service` | Subscription plans, token balances, payments and token reservations |
| `config-server` | Central configuration for Spring services |
| `eureka-server` | Service discovery |
| `ai-service` | Image classification with MobileNetV2 via ONNX Runtime |
| `watermark-service-py` | PNG watermark embed, detect, extract, visualize and capacity checks |
| `gui` | User interface for authentication, subscriptions and watermark operations |
| `postgres-db` | Shared PostgreSQL instance for auth and subscription schemas |

## Core Flow

1. The user logs in through `auth-server` and receives a JWT.
2. The GUI sends a watermark request to `watermark-service-py`.
3. The watermark service validates the JWT and checks the user's subscription state.
4. Paid operations reserve tokens in `subscription-service`.
5. The watermark service processes the image and optionally calls `ai-service`.
6. On success the reservation is consumed; on failure it is released.

## Business Rules

System plans define which operations a user can run and how many tokens they receive.

| Plan | Monthly tokens | Allowed operations |
|---|---:|---|
| `FREE` | 50 | `CAPACITY_CHECK`, `DETECT`, `EMBED_768` |
| `STANDARD` | 500 | `CAPACITY_CHECK`, `DETECT`, `EXTRACT`, `VISUALIZE`, `EMBED_768`, `EMBED_1024` |
| `PRO` | 2500 | All operations, including `AI_CLASSIFICATION` |

| Operation | Cost |
|---|---:|
| `CAPACITY_CHECK` | 0 |
| `DETECT` | 1 |
| `EXTRACT` | 2 |
| `VISUALIZE` | 3 |
| `EMBED_768` | 5 |
| `EMBED_1024` | 8 |
| `AI_CLASSIFICATION` | 2 |

Additional rules:

- Upgrades are allowed from `FREE` to `STANDARD`, `FREE` to `PRO` and `STANDARD` to `PRO`.
- Downgrades and duplicate purchases of the active plan are blocked.
- Paid plans are valid for one month from purchase or upgrade.
- Expired plans return to `FREE` with a reset token balance.
- Regular users can extract only their own watermarks.
- Administrators can detect, extract and visualize watermarks for any image.

## Watermarking Constraints

- Embed accepts PNG images only.
- Minimum image size for embedding is `1024x1024`.
- Images up to Full HD pixel count (`1920 * 1080`) use the `EMBED_768` tier.
- Larger images use the `EMBED_1024` tier.
- The GUI accepts images up to 20 MB.
- The output should remain PNG and should not be recompressed, resized or screenshotted if the watermark must survive.

## Quick Start

Create a local environment file:

```bash
cp .env.example .env
```

Start the full system:

```bash
docker compose up -d --build
```

Open the application:

```text
http://localhost:5173
```

Stop the system:

```bash
docker compose down
```

Remove containers and database volumes:

```bash
docker compose down -v
```

## Demo Accounts

| Login | Password | Role | Plan |
|---|---|---|---|
| `admin@gmail.com` | `admin` | `ADMIN` | `PRO` |
| `free@gmail.com` | `free` | `USER` | `FREE` |
| `standard@gmail.com` | `standard` | `USER` | `STANDARD` |
| `pro@gmail.com` | `pro` | `USER` | `PRO` |
| `lowbalance@gmail.com` | `lowbalance` | `USER` | `FREE`, low token balance |

## Local URLs

| Service | URL |
|---|---|
| GUI | http://localhost:5173 |
| Eureka | http://localhost:8761 |
| Config Server | http://localhost:8888/application/default |
| Auth Server | http://localhost:8081 |
| Subscription Service | http://localhost:8085 |
| Watermark Service | http://localhost:8082 |
| AI Service | http://localhost:8084 |
| SonarQube | http://localhost:9000 |
| PostgreSQL host port | `localhost:5433` |

## API Documentation

The consolidated OpenAPI specification is available in:

- [`stegocloud-openapi.json`](stegocloud-openapi.json)
- [`api-docs.html`](api-docs.html)
- [`docs/api/`](docs/api/README.md)

Because browsers block local file requests, serve the API viewer through a local HTTP server:

```bash
python3 -m http.server 8000
```

Then open:

```text
http://localhost:8000/api-docs.html
```

Service-specific API docs:

| Service | URL |
|---|---|
| Auth Server | http://localhost:8081/swagger-ui/index.html |
| Subscription Service | http://localhost:8085/swagger-ui/index.html |
| AI Service | http://localhost:8084/swagger-ui/index.html |
| Watermark Service | http://localhost:8082/docs |

## Useful Endpoints

Auth:

```http
POST /auth/login
POST /auth/validate?token=...
```

Subscription:

```http
GET  /api/subscriptions/plans
GET  /api/subscriptions/me
GET  /api/subscriptions/me/tokens
POST /api/payments/mock/sessions
POST /api/payments/mock/sessions/{sessionId}/succeed
POST /api/payments/mock/sessions/{sessionId}/fail
POST /api/payments/mock/sessions/{sessionId}/cancel
POST /api/tokens/reservations
POST /api/tokens/reservations/{reservationId}/consume
POST /api/tokens/reservations/{reservationId}/release
```

Watermark:

```http
POST /api/watermark/capacity
POST /api/watermark/embed
POST /api/watermark/detect
POST /api/watermark/extract
POST /api/watermark/visualize
GET  /health
```

## Development

Run Java tests:

```bash
./gradlew test
```

Run Java formatting and static checks:

```bash
./gradlew spotlessCheck checkstyleMain checkstyleTest
```

Run Python watermark-service tests:

```bash
cd watermark-service-py
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pytest -v
```

Run frontend checks:

```bash
cd gui
npm install
npm run check
npm run build
```

## SonarQube

Default local credentials:

```text
login: admin
password: admin
```

After the first login, change the password, generate a user token and put it in `.env` as `SONARQUBE_TOKEN`.

Run Java analysis:

```bash
docker compose --profile tools run --rm sonar-scan
```

## Documentation

- API reference: [`docs/api/`](docs/api/README.md)
- Project PDF documentation: [`dokumentacja/dokumentacja.pdf`](dokumentacja/dokumentacja.pdf)
- PlantUML diagrams: [`dokumentacja/`](dokumentacja/)
- Python watermark service details: [`watermark-service-py/README.md`](watermark-service-py/README.md)

## Team

251558, 251554, 251598, 251620, 251606, 247774
