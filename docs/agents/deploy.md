# Deployment — Railway

Auto-deploys on push to `main`. **Committed-but-unpushed code is never deployed** — this is the most common cause of "backend changes not taking effect."

Config: `railway.toml` + `Dockerfile` in repo root.

## Production Environment Variables

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | DB user |
| `DATABASE_PASSWORD` | DB password |
| `JWT_SECRET` | HMAC-SHA256 key (min 256 bits) |
| `SENTRY_DSN` | Sentry project DSN |
| `PORT` | HTTP port (Railway injects; defaults to 8080) |

## Profiles

- **`dev`** — `application-dev.yml`: localhost PostgreSQL, Sentry disabled. `DebugController` is `@Profile("dev")` only.
- **Production** — `application.yml`: env var substitution for all secrets.
