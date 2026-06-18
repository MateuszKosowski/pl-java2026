# StegoCloud

## Spis treści
- [Członkowie grupy](#czlonkowie-grupy)
- [Opis projektu](#opis-projektu)
- [Architektura](#architektura)
- [Główne zasady biznesowe](#glowne-zasady-biznesowe)
- [Mechanizmy Javy 21](#mechanizmy-javy-21)
- [Konta demo](#konta-demo)
- [Konfiguracja](#konfiguracja)
- [Uruchomienie](#uruchomienie)
- [Linki](#linki)
- [Dokumentacja API (OpenAPI)](#dokumentacja-api-openapi)
- [Przydatne endpointy](#przydatne-endpointy)
- [SonarQube](#sonarqube)
- [Uwagi techniczne](#uwagi-techniczne)

## Czlonkowie grupy

251558, 251554, 251598, 251620, 251606, 247774

## Opis projektu

StegoCloud to system mikroserwisowy do ukrywania i odczytywania zaszyfrowanych znakow wodnych w obrazach PNG. Projekt jest poliglotyczny: uslugi infrastrukturalne, autoryzacja, subskrypcje, tokeny i klasyfikacja AI sa zaimplementowane w Javie/Spring Boot, a wlasciwy silnik watermarkingu dziala jako osobny serwis Python/FastAPI.

Watermark jest szyfrowany z uzyciem AES-GCM, a nastepnie osadzany w obrazie PNG. Operacje watermarkingu sa kontrolowane przez system subskrypcji i tokenow.

## Architektura

| Modul | Technologia | Odpowiedzialnosc |
|---|---|---|
| `auth-server` | Java 21 / Spring Boot | logowanie, role uzytkownikow, wystawianie JWT |
| `subscription-service` | Java 21 / Spring Boot | plany subskrypcji, saldo tokenow, rezerwacje i zuzycie tokenow |
| `config-server` | Java 21 / Spring Cloud Config | centralna konfiguracja mikroserwisow |
| `eureka-server` | Java 21 / Spring Cloud Netflix Eureka | service discovery |
| `ai-service` | Java 21 / Spring Boot / ONNX Runtime | klasyfikacja obrazow modelem MobileNetV2 |
| `watermark-service-py` | Python / FastAPI | silnik PNG watermarkingu, szyfrowanie, embed/detect/extract/visualize |
| `gui` | Svelte / nginx | interfejs uzytkownika |
| `postgres-db` | PostgreSQL | baza danych dla `auth-server` i `subscription-service` |
| `sonarqube` | SonarQube | analiza jakosci kodu |

Pythonowy `watermark-service-py` jest runtime'owym serwisem watermarkingu i w Docker Compose wystepuje jako `watermark-service`. Rejestruje sie w Eurece pod logiczna nazwa `WATERMARK-SERVICE`.

## Glowne zasady biznesowe

System uzywa planow subskrypcji i tokenow. Kazda platna operacja najpierw rezerwuje tokeny w `subscription-service`. Po sukcesie tokeny sa zuzywane, a po bledzie rezerwacja jest zwalniana.

| Operacja | Koszt |
|---|---:|
| `CAPACITY_CHECK` | 0 |
| `DETECT` | 1 |
| `EXTRACT` | 2 |
| `VISUALIZE` | 3 |
| `EMBED_768` | 5 |
| `EMBED_1024` | 8 |
| `AI_CLASSIFICATION` | 2 |

| Plan | Tokeny / miesiac | Dozwolone operacje |
|---|---:|---|
| `FREE` | 50 | `CAPACITY_CHECK`, `DETECT`, `EMBED_768` |
| `STANDARD` | 500 | `CAPACITY_CHECK`, `DETECT`, `EXTRACT`, `VISUALIZE`, `EMBED_768`, `EMBED_1024` |
| `PRO` | 2500 | wszystkie operacje, wlacznie z `AI_CLASSIFICATION` |

Cykl zycia subskrypcji:

- dozwolone sa wylacznie przejscia `FREE -> STANDARD`, `FREE -> PRO` i `STANDARD -> PRO`,
- downgrade oraz ponowny zakup aktywnego planu sa blokowane po stronie backendu,
- platny plan jest wazny przez jeden miesiac od daty zakupu lub upgrade'u,
- upgrade rozpoczyna nowy miesieczny okres i dodaje pelna pule tokenow nowego planu do aktualnego salda,
- po wygasnieciu plan wraca do `FREE`, a saldo jest resetowane do 50 tokenow,
- rezerwacje rozpoczete przed wygasnieciem moga zostac rozliczone, ale wygasly plan nie pozwala rozpoczynac nowych operacji,
- finalizacja platnosci jest idempotentna i wymaga wlasciciela danej sesji platniczej.

Zasady watermarkingu:

- embed przyjmuje tylko obrazy PNG,
- minimalny rozmiar obrazu do embedowania to `1024x1024`,
- obrazy do limitu Full HD pixel count (`1920 * 1080`) uzywaja tieru `EMBED_768`,
- obrazy powyzej tego limitu uzywaja tieru `EMBED_1024`,
- klasyfikacja AI jest opcjonalna i wykonywana tylko wtedy, gdy plan oraz saldo tokenow na to pozwalaja,
- zwykly uzytkownik moze odczytywac tylko swoje watermarki,
- administrator moze wykonywac detect/extract/visualize dla kazdego obrazu.
- GUI przyjmuje obrazy o maksymalnym rozmiarze 20 MB i pokazuje date waznosci aktywnego planu.

## Mechanizmy Javy 21

W projekcie wykorzystano m.in.:

- `record` jako DTO i niemutowalne obiekty domenowe,
- `sealed interface` do modelowania decyzji rezerwacji tokenow,
- pattern matching for `switch` do obslugi wszystkich wariantow decyzji tokenowej,
- konfiguracje przez `@ConfigurationProperties`,
- Spring Cloud Config, Eureka, Spring Security, Flyway i Actuator.

## Konta demo

| Login | Haslo | Rola | Plan |
|---|---|---|---|
| `admin@gmail.com` | `admin` | `ADMIN` | `PRO` |
| `free@gmail.com` | `free` | `USER` | `FREE` |
| `standard@gmail.com` | `standard` | `USER` | `STANDARD` |
| `pro@gmail.com` | `pro` | `USER` | `PRO` |
| `lowbalance@gmail.com` | `lowbalance` | `USER` | `FREE`, niskie saldo tokenow |

## Konfiguracja

Przed uruchomieniem nalezy przygotowac plik `.env`. Najprosciej skopiowac `.env.example`:

```bash
cp .env.example .env
```

Przykladowe wymagane zmienne:

```env
JWT_SECRET_KEY=...
DB_USER=postgres
DB_PASSWORD=postgres
DB_NAME=stego_cloud
WATERMARK_APP_KEY=...
CONFIG_SERVER_USER=admin
CONFIG_SERVER_PASSWORD=admin
EUREKA_USER=admin
EUREKA_PASSWORD=admin
SONARQUBE_TOKEN=...
```

Konfiguracje mikroserwisow sa pobierane przez Spring Cloud Config.

Repozytorium z configami:

```text
https://github.com/bkolacinski/pl-java2026-config.git
```

Po zmianie nazwy repozytorium configow nalezy zaktualizowac `spring.cloud.config.server.git.uri` w `config-server/src/main/resources/application.yaml`.

## Uruchomienie

Zbudowanie i uruchomienie wszystkich podstawowych uslug:

```bash
docker compose up -d --build
```

Uruchomienie bez przebudowywania obrazow:

```bash
docker compose up -d
```

Przebudowanie tylko frontendu:

```bash
docker compose up -d --build gui
```

Przebudowanie tylko Pythonowego watermark-service:

```bash
docker compose build --pull --no-cache watermark-service
docker compose up -d watermark-service
```

Zatrzymanie systemu:

```bash
docker compose down
```

Wyczyszczenie kontenerow razem z wolumenami baz danych:

```bash
docker compose down -v
```

## Linki

| Usluga | URL |
|---|---|
| GUI | http://localhost:5173 |
| Eureka | http://localhost:8761 |
| Config Server | http://localhost:8888/application/default |
| Auth Server | http://localhost:8081 |
| Subscription Service | http://localhost:8085 |
| Watermark Service Python/FastAPI | http://localhost:8082 |
| AI Service | http://localhost:8084 |
| SonarQube | http://localhost:9000 |
| PostgreSQL host port | `localhost:5433` |

## Dokumentacja API (OpenAPI)

### Skonsolidowana dokumentacja
W głównym katalogu znajduje się plik `stegocloud-openapi.json` (połączone API wszystkich serwisów) oraz `api-docs.html` (interaktywny viewer).

Ze względu na zabezpieczenia przeglądarek (CORS), plik `api-docs.html` musi być uruchomiony przez serwer HTTP.

**Szybkie uruchomienie (Python):**
```bash
python3 -m http.server 8000
```
Następnie otwórz: [http://localhost:8000/api-docs.html](http://localhost:8000/api-docs.html)

### Dokumentacja poszczególnych serwisów
| Usluga | URL |
|---|---|
| Auth Server | http://localhost:8081/swagger-ui/index.html |
| Subscription Service | http://localhost:8085/swagger-ui/index.html |
| AI Service | http://localhost:8084/swagger-ui/index.html |
| Watermark Service | http://localhost:8082/docs |

## Przydatne endpointy

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

## SonarQube

Domyslne dane logowania:

```text
login: admin
password: admin
```

Po pierwszym logowaniu nalezy zmienic haslo, wygenerowac User Token i wpisac go do `.env` jako `SONARQUBE_TOKEN`.

Analiza modulow Java:

```bash
docker compose --profile tools run --rm sonar-scan
```

## Uwagi techniczne

- `watermark-service-py` jest serwisem wykonawczym dla watermarkingu, ale decyzje o planach i tokenach podejmuje `subscription-service`.
- `ai-service` korzysta z ONNX Runtime i modelu MobileNetV2.
- `subscription-service` uzywa osobnego schematu `subscription_schema`.
- `auth-server` uzywa schematu `auth_schema`.
- Oba serwisy korzystaja z tej samej instancji PostgreSQL w Docker Compose.
- Stary Java `watermark-service` nie jest juz czescia aktywnego builda.
