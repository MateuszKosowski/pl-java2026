## Członkowie grupy 
251558, 251554, 251598, 251620, 251606, 247774

## Architektura

System jest polyglotem. **Watermark Service to obecnie serwis w Pythonie (FastAPI)** —
zastąpił dawny moduł Java `watermark-service`, który został usunięty. W Eurece nadal
rejestruje się pod logiczną nazwą `WATERMARK-SERVICE`, więc dla klientów (GUI, Feign)
nic się nie zmienia.

| Moduł | Technologia | Odpowiedzialność |
|---|---|---|
| `auth-server` | Java / Spring Boot | uwierzytelnianie i wydawanie JWT |
| `config-server` | Java / Spring Cloud Config | centralna konfiguracja |
| `eureka-server` | Java / Spring Cloud | service discovery |
| `ai-service` | Java / Spring Boot | klasyfikacja obrazów (AI) |
| `watermark-service-py` | **Python / FastAPI** | steganografia PNG + szyfrowane (AES-GCM) znakowanie wodne |
| `gui` | React / nginx | interfejs użytkownika |

Krótko: usługi Java zajmują się uwierzytelnianiem, konfiguracją, discovery i AI,
natomiast Python obsługuje steganografię PNG i przetwarzanie zaszyfrowanych znaków wodnych.

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
