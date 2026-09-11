# Decision log

Non-obvious choices in this service, one line each. Format and rules: the monorepo root `DECISIONS.md`.

---

[2026-09-11] fantasy-yahoo-service: an unknown path answers 404 through its own `NoResourceFoundException` handler, copied from bff and espn-service (fantasy-workspace#33) — a shared advice library was rejected because the services deploy separately and it would couple their releases for about ten lines, and a catch-all that passes every Spring `ErrorResponse` 4xx through quietly was left for a separate decision across all four services rather than widening this fix.
[2026-09-11] fantasy-yahoo-service: only a dedicated `YahooUpstreamException`, thrown by `YahooFantasyClient` and `YahooTokenClient`, maps to 502 (#47) — rewording the config and crypto paths was rejected because it fixes those instances and leaves any future `IllegalStateException` blamed on Yahoo, and `YahooGrantRejectedException` stays separate rather than a subtype because its remedy is re-consent, not retry.
[2026-09-11] fantasy-yahoo-service: local `docker-compose.yml` moves from `postgres:17-alpine` down to `16-alpine` to match what Coolify runs in staging and prod, checked on both servers (#46) — upgrading the servers to 17 was rejected as a data migration on two live databases for no feature this service uses, while a local mismatch can hide a version-specific difference until deploy.
