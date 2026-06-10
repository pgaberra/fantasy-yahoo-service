# Deployment — fantasy-yahoo-service (Render web + Neon Postgres)

The web service runs on **Render** (Docker), defined in [`render.yaml`](./render.yaml).
Its database is its own external free **Neon** Postgres (not shared with
`fantasy-nhl-service`).

> **Why Neon and not a Render database?** Render's free tier allows only **one** managed
> Postgres per account, already used by `fantasy-db`. Neon's free tier gives a separate,
> persistent Postgres, keeping the "separate database per service" separation.

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — JDK 25 builds the boot jar, JRE 25 runs it |
| Database | **Neon** managed PostgreSQL (free plan), over TLS |
| Schema | Flyway migration (`V1`) runs automatically on startup |
| Port | `${PORT}` (Render injects it); 8088 locally |
| Health check | `GET /actuator/health` |
| Auto-deploy | On every push to `master` |

## ⚠️ The custom-domain requirement (Yahoo OAuth)

Yahoo rejects `localhost` and shared free-hosting domains (`*.onrender.com`) as the OAuth
**callback domain**. So the redirect URI **cannot** be `https://fantasy-yahoo-service.onrender.com/...`.
You need a **custom domain** pointed at this Render service:

1. Own a domain (any registrar). In Render → this service → **Settings → Custom Domains**,
   add e.g. `yahoo.yourdomain.com`. Render gives you a CNAME target; add that CNAME at your
   registrar. Render provisions free TLS.
2. In your **Yahoo developer app**, set the redirect URI to
   `https://yahoo.yourdomain.com/api/v1/yahoo/oauth/callback` (exact match).
3. Set `YAHOO_REDIRECT_URI` on this service to the same value.

## First-time setup

1. **Create the Neon database** ([neon.com](https://neon.com) → new project). Note host,
   db name, user, password from **Connection Details**. Fill the placeholders in
   `application-staging.yaml` (host/db/user) if you'll run the `staging` profile locally.
2. Render → **New → Blueprint** → connect the `fantasy-yahoo-service` repo. Apply.
3. Fill in the env vars Render prompts for (`sync: false`):
   - **DB:** `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (from Neon). `DB_PORT` (5432)
     and `DB_OPTIONS` (`?sslmode=require`) are already set in `render.yaml`.
   - **Internal auth:** `INTERNAL_API_KEY` (`openssl rand -hex 32`). Set the same value as
     `YAHOO_INTERNAL_API_KEY` on **fantasy-bff**.
   - **Yahoo OAuth:** `YAHOO_CLIENT_ID`, `YAHOO_CLIENT_SECRET` (from the Yahoo app),
     `YAHOO_REDIRECT_URI` (the custom-domain callback above), `YAHOO_STATE_SECRET`
     (`openssl rand -hex 32`), `TOKEN_ENCRYPTION_KEY` (`openssl rand -base64 32`),
     `WEB_POST_CONNECT_URL` (the deployed web app URL).
4. Add the custom domain (see above) and update the Yahoo app + `YAHOO_REDIRECT_URI`.
5. Verify health: `https://yahoo.yourdomain.com/actuator/health` → `{"status":"UP"}`.

## Wiring the BFF to this service

The BFF reaches this service via `YAHOO_SERVICE_URL` and authenticates with
`YAHOO_INTERNAL_API_KEY` (phase 3 wires the BFF client). Set on the BFF's Render service:

| BFF env var | Value |
|---|---|
| `YAHOO_SERVICE_URL` | this service's URL (the custom domain or its onrender URL — internal calls aren't subject to Yahoo's domain rule) |
| `YAHOO_INTERNAL_API_KEY` | same value as this service's `INTERNAL_API_KEY` |

## Service-to-service security

Every `/api/**` request must carry `X-Internal-Api-Key: <secret>`, **except**
`/api/v1/yahoo/oauth/callback` (Yahoo's browser redirect can't send it — secured by the
signed `state`) and `/actuator/**`. When `INTERNAL_API_KEY` is unset (local dev) the
filter is disabled.

## Free-tier caveats

- The web service sleeps when idle (cold starts of tens of seconds). The BFF's timeout to
  this service should be generous (see the BFF's staging timeouts).
- Neon free Postgres is small and not backed up. Tokens are stored encrypted regardless.
