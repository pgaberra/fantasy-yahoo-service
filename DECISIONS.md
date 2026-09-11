# Decision log

Non-obvious choices in this service, one line each. Format and rules: the monorepo root `DECISIONS.md`.

---

[2026-09-11] fantasy-yahoo-service: an unknown path answers 404 through its own `NoResourceFoundException` handler, copied from bff and espn-service (fantasy-workspace#33) — a shared advice library was rejected because the services deploy separately and it would couple their releases for about ten lines, and a catch-all that passes every Spring `ErrorResponse` 4xx through quietly was left for a separate decision across all four services rather than widening this fix.
[2026-09-11] fantasy-yahoo-service: the four OAuth secrets are checked by Bean Validation on `YahooOAuthProperties` at binding (#45), including the 32-byte key length — a `@PostConstruct` check like `InternalApiKeyFilter`'s was rejected because it would sit in whichever bean happens to read the value, and a check in the record's constructor because unit tests build the record by hand with blanks; both staging and prod containers were confirmed to carry all four, non-blank, before merging.
