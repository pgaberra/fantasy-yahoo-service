# fantasy-yahoo-service

Yahoo integration microservice for the fantasy hockey tool. Owns the per-user OAuth 2.0
flow with the Yahoo Fantasy Sports API, stores each user's tokens (encrypted) in Postgres,
and serves a user's fantasy **league settings** to `fantasy-bff`.

## Quick start

```bash
docker compose up -d          # Postgres on host port 5434, DB "fantasy_yahoo"
./gradlew bootRun             # starts on http://localhost:8088
```

Swagger UI: http://localhost:8088/swagger-ui.html

> The live OAuth round-trip needs a Yahoo developer app and a **custom callback domain**
> (Yahoo rejects `localhost` and `*.onrender.com`). See `DEPLOYMENT.md`.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/yahoo/oauth/authorize-url?appUserId=` | Build the Yahoo consent URL (signed state) |
| `GET` | `/api/v1/yahoo/oauth/callback?code&state` | Yahoo redirect → exchange code, store tokens, 302 back to web |
| `GET` | `/api/v1/yahoo/oauth/connection?appUserId=` | Whether the user has connected Yahoo |
| `GET` | `/api/v1/yahoo/leagues?appUserId=` | The user's NHL fantasy leagues |
| `GET` | `/api/v1/yahoo/leagues/{leagueKey}/settings?appUserId=` | A league's scoring + roster settings |
| `GET` | `/actuator/health` | Health check |

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` | localhost / 5432 / fantasy_yahoo / fantasy / _(none)_ | Postgres connection |
| `PORT` | 8088 | HTTP port |
| `INTERNAL_API_KEY` | _(blank)_ | If set, every request (except `/actuator/**` and the OAuth callback) must send `X-Internal-Api-Key` |
| `YAHOO_CLIENT_ID` / `YAHOO_CLIENT_SECRET` | _(blank)_ | Yahoo developer app credentials |
| `YAHOO_REDIRECT_URI` | localhost callback | Must match the URI registered with Yahoo exactly |
| `YAHOO_STATE_SECRET` | _(blank)_ | HMAC key for the OAuth state |
| `TOKEN_ENCRYPTION_KEY` | _(blank)_ | Base64 256-bit AES key for token encryption (`openssl rand -base64 32`) |
| `WEB_POST_CONNECT_URL` | http://localhost:4200 | Where the browser is sent after connect |

Secrets come only from env vars — never commit them. See `CLAUDE.md` for architecture
and conventions.
