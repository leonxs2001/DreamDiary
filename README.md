# Dream Diary MCP Server

Spring Boot 3 / Java 21 application with:
- REST API for dream entries (`/api/**`)
- MCP JSON-RPC endpoint (`/mcp`)
- Legacy bearer API key auth (existing method)
- OAuth 2.0 Authorization Code + PKCE for Claude custom connectors

## Prerequisites

- Java 21
- Maven 3.9+

## Environment (.env)

`.env` is loaded via:

```properties
spring.config.import=optional:file:.env[.properties]
```

Required core variables:

- `SQLITE_DB_PATH`
- `DREAM_DIARY_API_KEY`

Required OAuth variables (all static):

- `DREAM_DIARY_OAUTH_USERNAME`
- `DREAM_DIARY_OAUTH_PASSWORD`
- `DREAM_DIARY_OAUTH_CLIENT_ID`
- `DREAM_DIARY_OAUTH_CLIENT_SECRET`
- `DREAM_DIARY_OAUTH_REDIRECT_URIS`
- `DREAM_DIARY_OAUTH_ISSUER_URL`

Optional OAuth variables:

- `DREAM_DIARY_OAUTH_SCOPES` (default: `dreamdiary.read dreamdiary.write`)
- `DREAM_DIARY_OAUTH_TOKEN_TTL` (default: `PT8H`)
- `DREAM_DIARY_OAUTH_REFRESH_TOKEN_TTL` (default: `P30D`)
- `DREAM_DIARY_OAUTH_AUTH_CODE_TTL` (default: `PT5M`)

General optional:

- `OPENAPI_SERVER_URL`

## Run

```bash
mvn clean test
mvn spring-boot:run
```

App URL: `http://localhost:8080`

## Authentication

### 1) Legacy API key (still supported)

Use on `/api/**` and `/mcp`:

```http
Authorization: Bearer <DREAM_DIARY_API_KEY>
```

### 2) OAuth for Claude connector

This server now exposes the endpoints Claude expects:

- `/.well-known/oauth-protected-resource`
- `/.well-known/oauth-protected-resource/mcp`
- `/.well-known/oauth-authorization-server`
- `/.well-known/oauth-authorization-server/mcp`
- `/oauth/authorize`
- `/oauth/token`

Flow:

1. Call `/mcp` without token -> server returns `401` + `WWW-Authenticate` with `resource_metadata`.
2. Claude reads discovery metadata.
3. Claude opens `/oauth/authorize` with PKCE params.
4. User logs in with static `.env` username/password.
5. Claude exchanges code on `/oauth/token` (with static client credentials).
6. Claude retries MCP call with `Authorization: Bearer <access_token>`.

## MCP Usage

Endpoint: `POST /mcp`

Initialize:

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {}
}
```

List tools:

```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/list",
  "params": {}
}
```

Call tool (`createDreamEntry`):

```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "createDreamEntry",
    "arguments": {
      "text": "Ich habe von einem leuchtenden Ozean getraeumt.",
      "dreamDate": "2026-03-23"
    }
  }
}
```

Implemented MCP tools:
- `createDreamEntry`
- `searchDreamEntries`
- `getDreamEntryById`
- `updateDreamEntryText`
- `deleteDreamEntry`

## OpenAPI

- Runtime JSON: `http://localhost:8080/v3/api-docs`
- Runtime YAML: `http://localhost:8080/openapi.yaml`
- Repo spec: `openapi.yaml`

The spec documents:
- legacy bearer + OAuth2 authorizationCode security schemes
- OAuth discovery endpoints
- `/oauth/authorize` and `/oauth/token`
- MCP endpoint `/mcp`
