# dream-diary

REST-Anwendung für ein Traumtagebuch auf Basis von Spring Boot 3, Java 21 und Maven.  
Die Anwendung enthält:

- OAuth2 Authorization Server (inkl. Login-Seite via Spring Security Form Login)
- geschützte Resource-API für Traumeinträge
- SQLite-Persistenz
- OpenAPI-Dokumentation für OpenAI GPT Actions

## Architektur (kurz)

- `security`: OAuth2 Authorization Server + JWT Resource Server in derselben App
- `dreamentry`: Controller, Service, Repository, Entity, DTOs
- `common`: einheitliches Fehlerformat und globale Fehlerbehandlung
- `config`: OpenAPI-Konfiguration und Initialisierung des SQLite-Verzeichnisses

## Voraussetzungen

- Java 21
- Maven 3.9+

## Lokaler Start

1. `.env` prüfen/anpassen (liegt bereits im Projekt).
2. Build und Tests:
   ```bash
   mvn clean test
   ```
3. Anwendung starten:
   ```bash
   mvn spring-boot:run
   ```

Die App läuft standardmäßig unter `http://localhost:8080`.

## ENV-Variablen

Die Anwendung lädt `.env` automatisch über `spring.config.import=optional:file:.env[.properties]`.

Pflicht-/Kernvariablen:

- `SQLITE_DB_PATH`  
  Beispiel: `./data/dream-diary.db`
- `DREAM_DIARY_USERS`  
  Format: `username:password,username2:password2`  
  Beispiel: `alice:secret123,bob:topsecret456`
- `OAUTH_CLIENT_ID`
- `OAUTH_CLIENT_SECRET`
- `OAUTH_REDIRECT_URI`
- `OAUTH_SCOPES` (optional, Default: `openid,profile,dream.read,dream.write`)
- `OAUTH_ISSUER` (Default: `http://localhost:8080`)

OpenAPI-Helfer:

- `OPENAPI_SERVER_URL`
- `OPENAPI_AUTHORIZATION_URL`
- `OPENAPI_TOKEN_URL`

Hinweise:

- Benutzer werden ausschließlich aus `DREAM_DIARY_USERS` geladen.
- Ungültige Benutzerdefinitionen führen zu einem klaren Startup-Fehler (fail-fast).
- Passwörter aus der ENV werden beim Laden mit BCrypt gehasht.
- Das Verzeichnis für `SQLITE_DB_PATH` wird beim Start automatisch erzeugt.

## OAuth2-Setup (für OpenAI GPT Actions)

### Relevante Endpunkte

- Authorization URL: `http://localhost:8080/oauth2/authorize`
- Token URL: `http://localhost:8080/oauth2/token`
- Login-Seite: `http://localhost:8080/login`

### Werte im GPT-Action-Editor

- Auth type: `OAuth`
- Client ID: Wert aus `OAUTH_CLIENT_ID`
- Client Secret: Wert aus `OAUTH_CLIENT_SECRET`
- Authorization URL: `https://<deine-domain>/oauth2/authorize`
- Token URL: `https://<deine-domain>/oauth2/token`
- Scope: z. B. `dream.read dream.write` (optional zusätzlich `openid profile`)
- Redirect/Callback:
  - Der im GPT-Editor angezeigte Callback muss exakt in `OAUTH_REDIRECT_URI` stehen.
  - Beispiel: `https://chat.openai.com/aip/oauth/callback` (nur Beispiel, im Editor prüfen).

## OpenAPI für GPT Actions

Optionen:

- Laufende JSON-Spec: `http://localhost:8080/v3/api-docs`
- Statische YAML-Spec (zur direkten URL-Nutzung): `http://localhost:8080/openapi.yaml`
- Datei im Repo: `openapi.yaml`

Im GPT-Editor kannst du entweder:

1. die `openapi.yaml` direkt einfügen, oder
2. eine öffentlich erreichbare HTTPS-URL zur Spec hinterlegen.

## API-Verhalten

- Basis-Pfad: `/api/dream-entries`
- `createdAt` wird serverseitig gesetzt.
- `updatedAt` wird beim Text-Update gesetzt.
- Zeitstempel sind UTC / ISO-8601.
- Filterregel:
  - `day` ist führend.
  - Kombination von `day` mit `start` oder `end` gibt `400 Bad Request`.

## Beispiel-cURL (mit Access Token)

`ACCESS_TOKEN` muss ein gültiger OAuth2-Access-Token mit passenden Scopes sein.

Create:

```bash
curl -X POST "http://localhost:8080/api/dream-entries" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"text":"Ich flog über eine Stadt aus Glas."}'
```

Patch Text:

```bash
curl -X PATCH "http://localhost:8080/api/dream-entries/1/text" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"text":"Dann wurde der Himmel golden."}'
```

Filter by day:

```bash
curl "http://localhost:8080/api/dream-entries?day=2026-03-22&page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Filter by timespan:

```bash
curl "http://localhost:8080/api/dream-entries?start=2026-03-22T00:00:00Z&end=2026-03-23T00:00:00Z" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Filter by text query:

```bash
curl "http://localhost:8080/api/dream-entries?q=meer" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Healthcheck

- `GET /actuator/health` ist ohne Authentifizierung erreichbar.

## Deployment-Hinweise (GPT Actions)

- OpenAI GPT Actions benötigen öffentlich erreichbare HTTPS-Endpunkte.
- Setze in Produktion:
  - `OAUTH_ISSUER=https://<deine-domain>`
  - `OPENAPI_SERVER_URL=https://<deine-domain>`
  - `OPENAPI_AUTHORIZATION_URL=https://<deine-domain>/oauth2/authorize`
  - `OPENAPI_TOKEN_URL=https://<deine-domain>/oauth2/token`
- Achte darauf, dass Reverse Proxy / Load Balancer korrekte `X-Forwarded-*` Header setzen.
