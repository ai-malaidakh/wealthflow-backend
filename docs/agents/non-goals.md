# Intentionally Deferred — Do Not Build Yet

> Read this when unsure whether a feature is in scope for the current phase.

| Feature | Status | Note |
|---|---|---|
| Rate limiting (bucket4j) | ❌ Pre-production | Add before real user load. Not blocking MVP. `bucket4j` not yet in `pom.xml`. |
| Refresh token revocation | ❌ Phase 4 hardening | Currently stateless JWTs. A revocation table is needed for "remove device". Build alongside email verification. |
| Email verification on signup | ❌ Phase 4 remaining | `email_verified` flag doesn't exist on `User` yet. Needs V13 migration + verification token table. Required before app store submission. |
| Password reset flow | ❌ Phase 4 remaining | `POST /api/auth/forgot-password` + `POST /api/auth/reset-password`. Required before app store submission. |
| GDPR hard purge (`DELETE /api/users/me`) | ❌ Phase 6 remaining | Hard delete all user data. Required before app store submission. |
| Multi-currency conversion | ❌ v2 | `currency` column exists; conversion logic is deferred. |
| Bank API / Plaid | ❌ v2 | — |
| AI categorization | ❌ v2 | — |
| Recurring transaction auto-generation | ❌ Phase 8D | — |
| Push notification service | ❌ Phase 7+ | — |
| Row-Level Security (PostgreSQL RLS) | ❌ Post-launch | Recommended hardening layer on top of existing CHECK constraints + tenant query filters. |
| CORS configuration | ❌ Before Phase 8 | Required before any browser-based client can call the API. Add `CorsConfigurationSource` bean before Phase 8 web app starts. |
| Phase 8 web API additions | ❌ After Phase 7 gate | Pagination, advanced filters, report aggregations. Do not build until Phase 7 mobile launch decision gate is passed. |
