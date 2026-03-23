# dream-diary

REST-Anwendung für ein Traumtagebuch mit Spring Boot 3, Java 21, Maven und SQLite.  
Die API ist über einen statischen Bearer-API-Key abgesichert, der aus `.env` geladen wird.

## Architektur (kurz)

- `security`: API-Key-basierte Authentifizierung für `/api/**`
- `dreamentry`: Controller, Service, Repository, Entity und DTOs
- `common`: globales Fehlerformat und Exception-Handling
- `config`: SQLite-Verzeichnis-Initialisierung und OpenAPI-Konfiguration

## Voraussetzungen

- Java 21
- Maven 3.9+

## Lokaler Start

1. `.env` prüfen/anpassen.
2. Build und Tests:
   ```bash
   mvn clean test
   ```
3. Anwendung starten:
   ```bash
   mvn spring-boot:run
   ```

Die App läuft auf `http://localhost:8080`.

## ENV-Variablen

`.env` wird automatisch geladen über:

```properties
spring.config.import=optional:file:.env[.properties]
```

Pflichtvariablen:

- `SQLITE_DB_PATH`  
  Beispiel: `./data/dream-diary.db`
- `DREAM_DIARY_API_KEY`  
  Beispiel: `super-long-random-secret-api-key`

Optional:

- `OPENAPI_SERVER_URL`  
  Beispiel lokal: `http://localhost:8080`  
  Beispiel Produktion: `https://your-domain.example.com`

Hinweise:

- `DREAM_DIARY_API_KEY` wird beim Start validiert (fail-fast bei leerem Wert).
- Das Verzeichnis für `SQLITE_DB_PATH` wird beim Start automatisch erstellt.

## Authentifizierung

Alle Endpunkte unter `/api/**` benötigen:

```http
Authorization: Bearer <DREAM_DIARY_API_KEY>
```

`/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**` und `/openapi.yaml` sind öffentlich.

## OpenAPI für GPT Actions

- Laufende JSON-Spec: `http://localhost:8080/v3/api-docs`
- Statische YAML-Spec: `http://localhost:8080/openapi.yaml`
- Repo-Datei: `openapi.yaml`

### OpenAI GPT Action Setup (API Key)

Im GPT-Action-Editor:

- Authentication Type: `API Key`
- API Key value: dein Wert aus `DREAM_DIARY_API_KEY`
- API Key location: `Authorization` Header
- Format: `Bearer <API_KEY>`

## API-Verhalten

- Basis: `/api/dream-entries`
- `createdAt` wird serverseitig gesetzt.
- `updatedAt` wird bei PATCH gesetzt.
- Zeitstempel sind UTC in ISO-8601.
- Filterregel:
  - `day` darf nicht mit `start`/`end` kombiniert werden.
  - Bei Kombination gibt die API `400 Bad Request`.

## Beispiel-cURL

PowerShell:

```powershell
$env:API_KEY="replace-with-your-api-key"
```

Create:

```bash
curl -X POST "http://localhost:8080/api/dream-entries" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"text":"Ich flog ueber eine Stadt aus Glas."}'
```

Patch Text:

```bash
curl -X PATCH "http://localhost:8080/api/dream-entries/1/text" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"text":"Dann wurde der Himmel golden."}'
```

Filter by day:

```bash
curl "http://localhost:8080/api/dream-entries?day=2026-03-23&page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer $API_KEY"
```

Filter by timespan:

```bash
curl "http://localhost:8080/api/dream-entries?start=2026-03-23T00:00:00Z&end=2026-03-24T00:00:00Z" \
  -H "Authorization: Bearer $API_KEY"
```

Filter by q:

```bash
curl "http://localhost:8080/api/dream-entries?q=meer" \
  -H "Authorization: Bearer $API_KEY"
```

Healthcheck:

```bash
curl "http://localhost:8080/actuator/health"
```

## Deployment-Hinweise

- Für GPT Actions muss die API öffentlich per HTTPS erreichbar sein.
- Setze in Produktion:
  - `OPENAPI_SERVER_URL=https://<deine-domain>`
- Verwende einen starken, langen API-Key und rotiere ihn regelmäßig.
