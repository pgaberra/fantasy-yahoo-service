# Deployment — fantasy-yahoo-service

Deployed via **Coolify** (self-hosted on Hetzner) as a **Docker service** plus its own
dedicated **PostgreSQL** database (separate from the other services'). Unlike db-service /
nhl-service, this service needs a **public domain** — Yahoo's OAuth callback is hit by the
user's browser — *and* an internal alias `yahoo-service:8088` the BFF calls.

| Environment | Public domain (OAuth callback) | Web (`WEB_POST_CONNECT_URL`) |
|---|---|---|
| production | `https://yahoo.slapstat.com` | `https://slapstat.com` |
| staging | `https://yahoo.staging.slapstat.com` | `https://staging.slapstat.com` |

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — JDK 25 builds the boot jar, JRE 25 runs it |
| Database | Dedicated Coolify PostgreSQL (`postgres:16-alpine`), same Docker network |
| Schema | Flyway migration (`V1`) runs automatically on startup (`ddl-auto: validate`) |
| Port | `${PORT}` (defaults to 8088); internal alias `yahoo-service:8088` for the BFF |
| Public domain | `yahoo.slapstat.com` / `yahoo.staging.slapstat.com` (Coolify Let's Encrypt; Cloudflare DNS-only) |
| Health check | `GET /actuator/health` |

## ⚠️ The custom-domain requirement (Yahoo OAuth)

Yahoo rejects `localhost` and shared free-hosting domains as the OAuth **callback domain**,
so the redirect URI must be on our own domain. The DNS A records `yahoo.slapstat.com` and
`yahoo.staging.slapstat.com` (→ the prod / staging server IPs, **DNS-only / grey cloud**)
already exist so Coolify can provision TLS. In the **Yahoo developer app**, register the
redirect URI(s) — and set `YAHOO_REDIRECT_URI` to the exact same value:

- prod: `https://yahoo.slapstat.com/api/v1/yahoo/oauth/callback`
- staging: `https://yahoo.staging.slapstat.com/api/v1/yahoo/oauth/callback`

If a Yahoo app allows only one redirect URI, use one app per environment.

## Environment variables (set in Coolify, per environment)

| Key | Value |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_NAME` | the dedicated Coolify Postgres (UUID alias / `5432` / `postgres` / `postgres`) |
| `DB_PASSWORD` | the password Coolify generated for that Postgres |
| `INTERNAL_API_KEY` | shared secret for BFF → this service. **Same value** as the BFF's `YAHOO_INTERNAL_API_KEY`. |
| `YAHOO_CLIENT_ID` | from the Yahoo developer app (public) |
| `YAHOO_CLIENT_SECRET` | from the Yahoo developer app (**secret** — set directly in Coolify) |
| `YAHOO_REDIRECT_URI` | the exact callback registered with Yahoo (see above) |
| `YAHOO_STATE_SECRET` | HMAC key for the OAuth `state` (`openssl rand -hex 32`) |
| `TOKEN_ENCRYPTION_KEY` | base64 256-bit AES key for stored tokens (`openssl rand -base64 32`) |
| `WEB_POST_CONNECT_URL` | the web app URL the browser returns to after connect |
| `YAHOO_SCOPE` | optional; defaults to `fspt-r` (Fantasy read) |

All secrets are **environment-specific** — staging and prod never share keys.

## First-time setup (per environment)

1. Create a dedicated **PostgreSQL** resource in Coolify; note its UUID (→ `DB_HOST`) and
   generated password (→ `DB_PASSWORD`).
2. Create an application from this repo (GitHub App source, **Dockerfile** build pack) on
   the target server/environment. Set `custom_network_aliases` to `yahoo-service` **and**
   the public **Domain** (`https://yahoo.slapstat.com` / `…staging…`).
3. Set the env vars above. `INTERNAL_API_KEY` must equal the BFF's `YAHOO_INTERNAL_API_KEY`.
4. Register the redirect URI with the Yahoo app and set `YAHOO_CLIENT_ID` /
   `YAHOO_CLIENT_SECRET` / `YAHOO_REDIRECT_URI` accordingly.
5. Deploy. Verify health: `https://yahoo.slapstat.com/actuator/health` → `{"status":"UP"}`.

## Wiring the BFF to this service

The BFF reaches this service over the internal network (not the public domain):

| BFF env var | Value |
|---|---|
| `YAHOO_SERVICE_URL` | `http://yahoo-service:8088` |
| `YAHOO_INTERNAL_API_KEY` | same value as this service's `INTERNAL_API_KEY` |

## Service-to-service security

Every `/api/**` request must carry `X-Internal-Api-Key: <secret>`, **except**
`/api/v1/yahoo/oauth/callback` (Yahoo's browser redirect can't send it — secured instead by
the signed `state`) and `/actuator/**`. When `INTERNAL_API_KEY` is unset (local dev) the
filter is disabled. Tokens are always stored encrypted (AES-GCM) regardless of environment.
