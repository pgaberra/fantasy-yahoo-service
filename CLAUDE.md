# CLAUDE.md — fantasy-yahoo-service

Yahoo integration microservice for the fantasy hockey tool. It owns the **per-user
OAuth 2.0 flow** with the Yahoo Fantasy Sports API, stores each user's tokens
(encrypted) in its own Postgres database, and exposes a REST API the BFF consumes to
read a user's fantasy **league settings** (scoring categories, roster positions, …).

> ⚠️ Guarded by a shared `X-Internal-Api-Key` header (see `InternalApiKeyFilter`). The
> one exception is `/api/v1/yahoo/oauth/callback`, which Yahoo's browser redirect hits
> directly — it is secured by a signed `state` parameter instead, not the API key.

## OAuth in one picture

```
web "Connect Yahoo" → BFF → POST /api/v1/yahoo/oauth/authorize-url?appUserId=…
   → service returns the Yahoo consent URL (signed `state` carries appUserId)
   → browser → Yahoo consent → GET /api/v1/yahoo/oauth/callback?code&state
   → service verifies state, exchanges code→tokens, stores them encrypted,
     302-redirects the browser back to ${WEB_POST_CONNECT_URL}?yahoo=connected
later: BFF → GET /api/v1/yahoo/leagues / …/settings (uses the stored tokens; phase 2)
```

## Tech stack

- Java 25, Spring Boot 4.0.5, Gradle (wrapper: `./gradlew`)
- Spring WebMVC (virtual threads), Spring Data JPA, Bean Validation, Actuator
- `RestClient` for Yahoo's OAuth + Fantasy API; JDK `Cipher` (AES-GCM) for token encryption
- PostgreSQL (runtime), Flyway migrations
- Tests: JUnit 5, H2 in-memory (PostgreSQL mode)
- springdoc OpenAPI / Swagger UI

## Common commands

```bash
docker compose up -d     # start Postgres (DB fantasy_yahoo, host port 5434)
./gradlew build          # compile + test (CI: ./gradlew build --no-daemon)
./gradlew test           # tests only (H2, no Postgres needed)
./gradlew bootRun        # run locally (requires Postgres via docker compose above)
```

Swagger UI (when running): `http://localhost:8088/swagger-ui.html`

## Architecture (`src/main/java/com/fantasy/yahoo/`)

- `oauth/` — the OAuth flow + token lifecycle:
  - `YahooOAuthToken` / `YahooOAuthTokenRepository` — JPA entity (`yahoo_oauth_tokens`),
    keyed by `app_user_id`; tokens stored as AES-GCM ciphertext.
  - `TokenCipher` — AES-GCM encrypt/decrypt (`TOKEN_ENCRYPTION_KEY`, base64 256-bit).
  - `OAuthStateCodec` — HMAC-signed, short-lived `state` carrying the app user id
    (`YAHOO_STATE_SECRET`); CSRF protection without a server-side state table.
  - `YahooTokenClient` — calls Yahoo's `/oauth2/get_token` (code exchange + refresh).
  - `YahooOAuthService` — builds the authorize URL, handles the callback (verify→exchange
    →store), and hands out a valid access token (refreshing transparently — phase 2 use).
  - `YahooOAuthController` — `/api/v1/yahoo/oauth/{authorize-url,callback,connection}`.
  - `dto/` — `AuthorizeUrlResponse`, `ConnectionResponse`.
- `league/` — fantasy league data:
  - `YahooFantasyClient` — `RestClient` over the Yahoo Fantasy API; returns `JsonNode`
    (Yahoo's JSON is deeply nested with numeric-keyed objects mixed into arrays).
  - `YahooLeagueService` — parses that JSON defensively into clean DTOs; gets a valid
    access token from `YahooOAuthService` (refreshing as needed).
  - `YahooLeagueController` — `GET /api/v1/yahoo/leagues` and
    `…/leagues/{leagueKey}/settings`.
  - `dto/` — `LeaguesResponse`/`LeagueSummary`, `LeagueSettingsResponse` (+ `StatCategory`,
    `RosterSlot`).
- `config/` — `OpenApiConfig` (pins server URL to `/`), `YahooOAuthProperties`
  (`@ConfigurationProperties("yahoo.oauth")`), `YahooRestClientConfig` (login + API
  `RestClient`s), `InternalApiKeyFilter` (API-key auth; exempts the callback).
- `exception/` — `YahooNotConnectedException`, `ErrorDto`, `GlobalExceptionHandler`.
- `players/` — the cached player read model and the job that refreshes it from Yahoo. The
  model is split along the line the data itself splits on:
  - `skaters` / `goalies` — **who is in the league now**: identity, team, sweater number,
    eligible positions, headshot. Turns over between seasons.
  - `skater_seasons` / `goalie_seasons` — **one stat line per player and season**, keyed by
    `(player_id, season)` where season is the start year. A finished season's numbers never
    change again, so nothing overwrites them; a departed player's rows go by cascade.

  It used to be one unlabelled stat line per player, which meant caching a new season wrote
  over the previous one. That collides with how the app is used: the season being **collected**
  is the one being played, while the season being **shown** as a projection's reference is the
  one that finished. Those are different for most of the year, so they are now different
  settings in different services — `SYNC_YAHOO_SEASON` here says what to collect, and the BFF
  decides what to show by asking `/api/v1/players/*?season=`.

  `SYNC_YAHOO_DISABLED` remains the off-season switch: it skips the scheduled run entirely,
  for the months when Yahoo has no active game to call at all. A failed run logs at `ERROR`
  and therefore reaches Sentry, which is the signal that the season has ended.

**Phase status:** OAuth connect flow + encrypted token storage + `/connection` **and**
league discovery + settings are implemented. The BFF wiring lands in a parallel PR and the
service deploys on Coolify behind a public callback domain (`yahoo.slapstat.com`). Remaining:
the **web** Connect/picker UI.

## Database & config

- `application.yaml`: datasource
  `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:fantasy_yahoo}`,
  `ddl-auto: validate` (Flyway owns the schema), `server.port=${PORT:8088}`.
- `yahoo.oauth.*` — OAuth config. **Secrets** (`client-id/secret`, `state-secret`,
  `token-encryption-key`) default to empty so the app still boots for tests/CI; the OAuth
  endpoints just fail at call time when unset. Non-secret URLs + `scope` carry defaults.
- **Yahoo redirect URI gotcha:** Yahoo rejects `localhost` and shared free-hosting domains
  as the callback domain. Each environment therefore needs a **custom domain**
  (`yahoo.slapstat.com` / `yahoo.staging.slapstat.com`); `YAHOO_REDIRECT_URI` must match the
  value registered with the Yahoo app exactly.
- Migrations live in `src/main/resources/db/migration/` (`V1`). **Schema changes = a new
  `V__` migration**, never edit an applied one.
- Tests use H2 in PostgreSQL mode, `ddl-auto: create-drop`, Flyway disabled.

## Conventions

- **No code comments unless asked.** Don't write code comments or documentation unless
  specifically asked to — prefer self-explanatory names. (Same AI guideline as the
  `fantasy-web` repo.)
- Feature-package layout. Keep endpoints under `/api/v1`.
- Tokens (access + refresh) are **always encrypted at rest** — never store or log a raw
  token. The state and encryption keys come only from env (never committed).

### Logging & error handling

**Never silence an error.** Every `@RestControllerAdvice` must have a catch-all
`@ExceptionHandler(Exception.class)` that **logs the full stack trace** (`log.error`)
and returns a consistent `ErrorDto`. Rules of thumb:

- **5xx / genuine faults** (unexpected exceptions, the upstream Yahoo API failing): log
  at `ERROR` with the exception.
- **4xx / expected client outcomes** (not-connected, invalid OAuth state): do **not** log
  as errors.
- The OAuth **callback** never returns a JSON error to the browser — it logs the failure
  and redirects back to the web app with `?yahoo=error`.

### OpenAPI annotations & spec snapshot (`specs/openapi.yaml`)

This service's spec is consumed by `fantasy-bff` to generate a typed client. Annotate
controllers with `@Tag`/`@Operation`/`@ApiResponse`. `OpenApiSpecSnapshotTest` boots the
app and asserts the committed `specs/openapi.yaml` matches the live spec. Regenerate:
```
./gradlew test -DupdateSpec=true   # rewrites specs/openapi.yaml
git add specs/openapi.yaml
```
`OpenApiConfig` pins the server URL to `/` so the spec is deterministic.

## CI / workflow

- `.github/workflows/pr-checks.yml`: `./gradlew build --no-daemon` on PRs to `master`.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

## Monorepo conventions

Shared across all five repos (`fantasy-web` → `fantasy-bff` → `fantasy-db-service` +
`fantasy-nhl-service` + `fantasy-yahoo-service`). The web talks only to the BFF;
inter-service calls use a shared `X-Internal-Api-Key` header.

### Secrets

**Never commit a password, API key, token, or any secret to git — in any environment**,
not even throwaway local-dev credentials, so the habit is absolute and we never risk
leaking (or reusing) a real one. Secrets come only from environment variables
(`${DB_PASSWORD}`, `${INTERNAL_API_KEY}`, `${YAHOO_CLIENT_SECRET}`, …) — no literal value
in `application*.yaml`. Non-secret connection details (host, port, db name, username) may
be committed. The local-dev DB password lives only in `docker-compose.yml`. Run a service
against a chosen DB with the `local` / `staging` Spring profiles:
`SPRING_PROFILES_ACTIVE=<profile> DB_PASSWORD=… ./gradlew bootRun`.

### Merging PRs

Branch → push → PR → checks pass → **squash merge** to `master`. GitHub squash uses the
**PR title** as the commit message, so make it a proper message (`feat: …`, `fix: …`), then
merge with an explicit subject:
```
gh pr merge <n> --squash --delete-branch \
  --subject "feat: describe the change (#<n>)" \
  --body "Optional longer description."
```
Never merge a PR titled "wip"/"draft".

### Commit messages

No attribution trailers (`attribution.commit` / `attribution.pr` are `""` in
`~/.claude/settings.json`, enforced at the tool level).

## Deployment

- Dockerized (multi-stage `Dockerfile`), deployed via **Coolify** (Hetzner) backed by its
  own dedicated Coolify Postgres, on both prod and staging. Unlike the other internal
  services it has a **public domain** (`yahoo.slapstat.com` / `yahoo.staging.slapstat.com`)
  for the OAuth callback, plus the internal alias `yahoo-service:8088` the BFF calls. See
  `DEPLOYMENT.md`. Set `INTERNAL_API_KEY` (same value the BFF sends as
  `YAHOO_INTERNAL_API_KEY`), the `YAHOO_*` OAuth vars, and `TOKEN_ENCRYPTION_KEY`. Health
  check: `/actuator/health`.
