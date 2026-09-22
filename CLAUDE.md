# CLAUDE.md — fantasy-yahoo-service

Yahoo integration microservice for the fantasy hockey tool. It owns the **per-user
OAuth 2.0 flow** with the Yahoo Fantasy Sports API, stores each user's tokens
(encrypted) in its own Postgres database, and exposes a REST API the BFF consumes for
a user's fantasy **league settings** (scoring categories, roster positions, …) **and the
cached player read model** — identity, eligible positions and per-season stats for every
player the app serves, refreshed by a scheduled sync (see `players/` below).

> ⚠️ Guarded by a shared `X-Internal-Api-Key` header (see `InternalApiKeyFilter`). The
> one exception is `/api/v1/yahoo/oauth/callback`, which Yahoo's browser redirect hits
> directly — it is secured by a signed `state` parameter instead, not the API key.

## OAuth in one picture

```
web "Connect Yahoo" → BFF → POST /api/v1/yahoo/oauth/authorize-url?appUserId=…
   → service returns the Yahoo consent URL (signed `state` carries appUserId)
   → browser → Yahoo consent → GET /api/v1/yahoo/oauth/callback?code&state
   → service verifies state, exchanges code→tokens, parks them encrypted under a one-time
     link code (yahoo_oauth_pending_links, 5 min), 302-redirects the browser to
     ${WEB_POST_CONNECT_URL}?yahoo=confirm&account=user|service#link=<code>
   → signed-in web → BFF → POST /api/v1/yahoo/oauth/complete {appUserId, code}
   → tokens attached only if appUserId started the flow; any other user gets 409 and the
     parked tokens are discarded
later: BFF → GET /api/v1/yahoo/leagues / …/settings (uses the stored tokens)
```

## Tech stack

- Java 25, Spring Boot 4.1.1, Gradle 9.7.1 (wrapper: `./gradlew`); Dependabot proposes updates weekly
- Spring WebMVC (virtual threads), Spring Data JPA, Bean Validation, Actuator
- `RestClient` for Yahoo's OAuth + Fantasy API; JDK `Cipher` (AES-GCM) for token encryption
- PostgreSQL (runtime), Flyway migrations
- Tests: JUnit 5, H2 in-memory (PostgreSQL mode)
- springdoc OpenAPI / Swagger UI

## Common commands

```bash
docker compose up -d     # start Postgres 16 (DB fantasy_yahoo, host port 5434)
./gradlew build          # compile + test + SpotBugs/FindSecBugs, fails on any finding (CI: ./gradlew build jacocoTestReport --no-daemon)
./gradlew test           # tests only (H2, no Postgres needed)
./gradlew bootRun        # run locally: Postgres via docker compose above, plus INTERNAL_API_KEY and the four OAuth secrets (see Database & config)
```

Swagger UI (when running): `http://localhost:8088/swagger-ui.html`

## Architecture (`src/main/java/com/fantasy/yahoo/`)

- `oauth/` — the OAuth flow + token lifecycle:
  - `YahooOAuthToken` / `YahooOAuthTokenRepository` — JPA entity (`yahoo_oauth_tokens`),
    keyed by `app_user_id`; tokens stored as AES-GCM ciphertext.
  - `TokenCipher` — AES-GCM encrypt/decrypt (`TOKEN_ENCRYPTION_KEY`, base64 256-bit).
  - `OAuthStateCodec` — HMAC-signed, 10-minute `state` carrying the app user id and a random
    nonce (`YAHOO_STATE_SECRET`). The signature stops a forged state; the nonce is what makes
    it single-use (next line).
  - `PendingOAuthState` / `PendingOAuthStateRepository` — JPA entity
    (`yahoo_oauth_pending_states`, V5): every authorize-url call stores its nonce, the callback
    **consumes** it, and an hourly job (`yahoo.oauth.pending-state-purge-cron`, default
    `0 20 * * * *`) purges expired rows. This table is the replay protection; the signature
    alone would let a captured state be reused until it expired. Do not treat it as unused.
  - `YahooTokenClient` — calls Yahoo's `/oauth2/get_token` (code exchange + refresh); throws
    `YahooGrantRejectedException` for Yahoo's `invalid_grant` (the stored token is dropped,
    re-consent is the only fix) and `YahooUpstreamException` for any other Yahoo failure.
  - `PendingYahooLink` / `PendingYahooLinkRepository` — JPA entity (`yahoo_oauth_pending_links`,
    V9): tokens from a finished consent, keyed by a SHA-256 of the one-time link code, waiting to
    be claimed. The state proves who *started* a flow, never whose browser *finished* it, so the
    callback must not attach tokens itself: a consent link sent to someone else would store their
    Yahoo account under the sender's. The claim through the BFF is what binds the two. Purged by
    the same hourly job.
  - `YahooOAuthService` — builds the authorize URL, handles the callback (verify→consume
    nonce→exchange→park), attaches a parked connection for the user who started it
    (`completeLink`), and hands out a valid access token (refreshing transparently).
  - `YahooOAuthController` — `/api/v1/yahoo/oauth/{authorize-url,callback,complete,connection}`.
  - `dto/` — `AuthorizeUrlResponse`, `CompleteLinkRequest`, `ConnectionResponse`.
- `league/` — fantasy league data:
  - `YahooFantasyClient` — `RestClient` over the Yahoo Fantasy API; returns `JsonNode`
    (Yahoo's JSON is deeply nested with numeric-keyed objects mixed into arrays).
  - `YahooLeagueService` — parses that JSON defensively into clean DTOs; gets a valid
    access token from `YahooOAuthService` (refreshing as needed).
  - `YahooLeagueController` — `GET /api/v1/yahoo/leagues`, `…/leagues/{leagueKey}/settings`,
    `…/teams`, `…/draft` and `…/free-agents`. **Teams** (from `/league/{key};out=draftresults,teams`)
    name the manager's own seat in `draftPosition`, taken from the slot its team holds in the first
    round and falling back to the league metadata's `draft_position`; the teams are ordered to match.
    `draftPosition` is **null when Yahoo names the seat nowhere** — a setup must then ask rather than
    read a seat off Yahoo's team order, which says nothing about who picks when. Yahoo tells no other
    team's seat before the draft's slots exist. **Free agents** are the players the league has
    available, free agents and waivers together (Yahoo's `status=A`), in Yahoo's actual-rank order
    so the first rows are the best available and a `limit` trims the tail rather than a slice;
    `availability` tells the two apart from the `ownership` subresource, and is `UNKNOWN` rather
    than a guess where Yahoo does not say. Read with the **user's own** token, since what is
    available is a fact about their league and the service account is not in it. The draft is one
    Yahoo call
    (`/league/{key};out=settings,draftresults,teams`): its status, the teams in first-round
    order and every pick Yahoo lists, which the BFF polls while a user follows a live draft.
  - `dto/` — `LeaguesResponse`/`LeagueSummary`, `LeagueSettingsResponse` (+ `StatCategory`,
    `RosterSlot`).
- `config/` — `OpenApiConfig` (pins server URL to `/`), `YahooOAuthProperties`
  (`@ConfigurationProperties("yahoo.oauth")`), `YahooRestClientConfig` (login + API
  `RestClient`s), `InternalApiKeyFilter` (API-key auth; exempts the callback).
- `exception/` — `ErrorDto`, `GlobalExceptionHandler`, `YahooUpstreamException` (thrown only by
  `YahooFantasyClient` / `YahooTokenClient`, and the only exception answered with 502; any other
  unexpected exception, `IllegalStateException` included, is the catch-all 500).
  `YahooNotConnectedException` (404) lives in `oauth/`.
- `player/` — live reads of Yahoo's player collections (not the cache), used by the sync:
  - `YahooPlayerService` — pages a game's or a league's player collection (25 per page) with the
    service account's token and parses each player's identity, headshot, eligible positions and
    season stat line.
  - `YahooPlayerController` — `GET /api/v1/yahoo/players` (the game-wide collection, kept for
    diagnosis; Yahoo refuses it today).
  - `dto/` — `YahooPlayerResponse`, `YahooSkaterStats`, `YahooGoalieStats`.
- `players/` — the cached player read model (served by `PlayerController`:
  `GET /api/v1/players/{skaters,goalies}?season=` and `…/{playerId}/headshot`) and the job that
  refreshes it from Yahoo (`SyncService`, `SyncScheduler`, `SyncController` at
  `/api/v1/sync`). The model is split along the line the data itself splits on:
  - `skaters` / `goalies` — **who is in the league now**: identity, team, sweater number,
    eligible positions, headshot. Turns over between seasons.
  - `skater_seasons` / `goalie_seasons` — **one stat line per player and season**, keyed by
    `(player_id, season)` where season is the start year. A finished season's numbers never
    change again, so nothing overwrites them; a departed player's rows go by cascade.

  The pool is read **through a league** the service account belongs to, not through the game.
  That is the only route the Fantasy API documents — its own client offers no game-wide player
  listing, only `League` methods — and the game-wide collection this used until June 2026 is now
  refused outright. The league is **discovered, not configured**: a league key contains the game
  key, so it changes every season, and pinning one would mean editing config each autumn. Join
  the service account to a league for the season and the sync finds it; a run with no league to
  read through fails loudly rather than guessing.

  It used to be one unlabelled stat line per player, which meant caching a new season wrote
  over the previous one. That collides with how the app is used: the season being **collected**
  is the one being played, while the season being **shown** as a projection's reference is the
  one that finished. Those are different for most of the year, so they are now different
  settings in different services — `SYNC_YAHOO_SEASON` here says what to collect, and the BFF
  decides what to show by asking `/api/v1/players/*?season=`.

  `YahooProbeService` (`GET /api/v1/sync/probe?gameKey=&season=&leagueKey=`) asks Yahoo one question and
  reports the answer as data: can the service account read this game's players for this season?
  A sync failure only says *something* was refused; telling a refused season from a refused game
  from a dead token means varying one input at a time and reading the raw status, which is what
  this is for. Give it a `leagueKey` and it asks a *league's* player collection instead: the
  granted Fantasy Sports scope talks about the user's own leagues, while a game's collection
  belongs to nobody in particular, so one may be served where the other is refused — and that
  difference is the diagnosis. `target=leagues` asks the floor question — can the account list
  its own leagues at all? — since a refusal there means no route into the Fantasy API is open
  and the question stops being which endpoint to use.
  `GET /api/v1/sync/probe/league?leagueKey=&resource=` hands back one of a league's resources
  (`settings`, `teams`, `draftresults`, or `draft` for the three together) exactly as Yahoo sent
  it, read with the service account's token, so a field can be seen to exist before code relies
  on it.
  `GET /api/v1/sync/leagues` lists the service account's own leagues
  with their keys, which both saves hunting for one and doubles as a test: if it succeeds while a
  game probe is refused, the account and its permission are fine. Neither reads into the cache nor
  writes anything, so both are safe to fire at will. The BFF exposes them to admins.

  `SYNC_YAHOO_DISABLED` remains the off-season switch: it skips the scheduled run entirely,
  for the months when Yahoo has no active game to call at all. A failed run logs at `ERROR`
  and therefore reaches Sentry, which is the signal that the season has ended.

## Database & config

- `application.yaml`: datasource
  `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:fantasy_yahoo}`,
  `ddl-auto: validate` (Flyway owns the schema), `server.port=${PORT:8088}`.
- `yahoo.oauth.*` — OAuth config. **Secrets** (`YAHOO_CLIENT_ID`, `YAHOO_CLIENT_SECRET`,
  `YAHOO_STATE_SECRET`, `TOKEN_ENCRYPTION_KEY`) have **no default** and are required in every
  environment, local runs included: `YahooOAuthProperties` is `@Validated`, so the app
  **refuses to start** when one is missing or blank, or when the key does not decode to 32
  bytes. The test `application.yaml` carries non-secret dummy values. Non-secret URLs +
  `scope` carry defaults.
- **Yahoo redirect URI gotcha:** Yahoo rejects `localhost` and shared free-hosting domains
  as the callback domain. Each environment therefore needs a **custom domain**
  (`yahoo.slapstat.com` / `yahoo.staging.slapstat.com`); `YAHOO_REDIRECT_URI` must match the
  value registered with the Yahoo app exactly.
- Migrations live in `src/main/resources/db/migration/` (`V1__…` onward). **Schema changes = a
  new `V__` migration**, never edit an applied one.
- Tests use H2 in PostgreSQL mode, `ddl-auto: create-drop`, Flyway disabled.

## Conventions

- **No code comments unless asked.** Don't write code comments or documentation unless
  specifically asked to — prefer self-explanatory names. (Same AI guideline as the
  `fantasy-web` repo.)
- Feature-package layout. Keep endpoints under `/api/v1`.
- Tokens (access + refresh) are **always encrypted at rest** — never store or log a raw
  token. The state and encryption keys come only from env (never committed).

### Error handling

The monorepo-wide rule (never silence an error; `ERROR` for 5xx, quiet for 4xx — here
not-connected and invalid OAuth state are the ordinary 4xx) lives in the root `CLAUDE.md`.
Specific here: the OAuth **callback** never returns a JSON error to the browser — it logs
the failure and redirects back to the web app with `?yahoo=error`.

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

- `.github/workflows/pr-checks.yml`: `./gradlew build jacocoTestReport --no-daemon` (tests +
  SpotBugs/FindSecBugs + coverage comment) on PRs to `master`. Not on push to `master`.
- `.github/workflows/tag-on-merge.yml` (push to `master`): tags a SemVer version from the squash
  commit's Conventional-Commit title, creates a **draft** GitHub Release, and stamps + redeploys
  **staging**. So **every merge deploys to staging**.
- `.github/workflows/promote-to-prod.yml` (a release is **published**, or manually with a
  `version`): deploys that tag to **production** and verifies the version production reports;
  a failure opens a `prod-promotion-failed` issue. A merge never reaches prod on its own.
- `.github/workflows/qodana.yml` (weekly cron, Mondays 06:00 UTC, plus manual): report-only
  Qodana scan; never fails.

## Monorepo conventions

The full set lives in the monorepo root `CLAUDE.md`: input validation at every boundary,
logging & error handling, secrets only from env, one worktree per agent, and the merge
procedure. In short — the web talks only to the BFF; inter-service calls carry a shared
`X-Internal-Api-Key` header. Branch → push → PR → checks pass → **squash merge** to `master`
(the PR title becomes the commit message; make it a proper `feat:`/`fix:` message and merge
with an explicit `--subject`). No attribution trailers. Secrets only from env, never
committed. Never merge a PR titled "wip"/"draft".

## Deployment

- Dockerized (multi-stage `Dockerfile`), deployed via **Coolify** (Hetzner) backed by its
  own dedicated Coolify Postgres, on both prod and staging. Unlike the other internal
  services it has a **public domain** (`yahoo.slapstat.com` / `yahoo.staging.slapstat.com`),
  which serves **only** the OAuth callback path, plus the internal alias `yahoo-service:8088`
  the BFF calls. The restriction is Coolify config that a domain edit can undo; see *Only the
  callback is public* in `DEPLOYMENT.md`. Set `INTERNAL_API_KEY` (same value the BFF sends as
  `YAHOO_INTERNAL_API_KEY`), the `YAHOO_*` OAuth vars, and `TOKEN_ENCRYPTION_KEY`. Health
  check: `/actuator/health`.
