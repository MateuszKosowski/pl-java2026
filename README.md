## Członkowie grupy 
251558, 251554, 251598, 251620, 251606, 247774

## Architecture

The system is polyglot. **The Watermark Service is now a Python (FastAPI) service** —
it replaced the old Java `watermark-service` module, which has been removed. It still
registers in Eureka under the logical name `WATERMARK-SERVICE`, so nothing changes for
clients (GUI, Feign).

| Module | Technology | Responsibility |
|---|---|---|
| `auth-server` | Java / Spring Boot | authentication and JWT issuing |
| `config-server` | Java / Spring Cloud Config | centralized configuration |
| `eureka-server` | Java / Spring Cloud | service discovery |
| `ai-service` | Java / Spring Boot | image classification (AI) |
| `watermark-service-py` | **Python / FastAPI** | PNG steganography + encrypted (AES-GCM) watermark processing |
| `gui` | React / nginx | user interface |

In short: the Java services handle authentication, configuration, discovery and AI,
while Python handles PNG steganography and encrypted watermark processing.

## Polecenia

Uruchomienie wszystkich modułów
```docker
docker compose up -d
```

## SonarQube
### Default credentials:
login: admin \
password: admin

Po pierwszym logowaniu należy zmienić hasło oraz utworzyć nowy User Token i wrzucić go do .env

Analiza problemów we wszystkich modułach
```docker
docker compose --profile tools run --rm sonar-scan
```

## Linki
Eureka - http://localhost:8761 \
Config Server - http://localhost:8888/application/default \
SonarQube - http://localhost:9000 \
Authentication Server - http://localhost:8081 \
Watermark Service (Python/FastAPI) - http://localhost:8082 \
Watermark Service API docs (Swagger) - http://localhost:8082/docs

## Repozytorium z configami
https://github.com/bkolacinski/pl-java2026-config.git
