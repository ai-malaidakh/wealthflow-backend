# WealthFlow Backend — Agent Brief

> **Scope:** Everything in `wealthflow-backend/`. Backend phases of the master plan.
> **Derived from:** `wealthflow-master-development-plan.md`. If this file and the master plan disagree, the master plan wins.
> **Orchestration layer:** For tasks spanning both repos, defer to `wealthflow-master/CLAUDE.md`.
> **Last synced with master plan:** April 20, 2026.

---

## 1. Identity & Scope

**WealthFlow Backend** is the Spring Boot API serving both the React Native mobile app and the future React/Vite web app. Separate GitHub remote — never merged into the `wealthflow` frontend monorepo.

**What this agent does not do:**
- Mobile app code (`wealthflow/apps/mobile/`) — frontend repo.
- Web app code (`wealthflow/apps/web/`) — Phase 8, not yet built.
- Design tokens or shared TypeScript types — `wealthflow/packages/`, not consumed by the backend.

**Current frontend dependency status:**
- Phase 3 sync is **LIVE** — `/api/sync/pull` and `/api/sync/push` are deployed and working.
- Phase 4 family sharing is **LIVE** — invite flow, join, member list, role update, remove member all work.
- Phase 6 CSV import is **LIVE** — `POST /api/import/coinkeeper` works.
- **What remains blocking mobile app store submission:** email verification, password reset (Phase 4), GDPR deletion endpoint (Phase 6).

---

## 2. Locked Tech Stack

Do not change these without escalating.

| Layer | Choice |
|---|---|
| Framework | Spring Boot 3.2.3 |
| Language | Java 17 |
| Build | Maven (`pom.xml`) |
| Database | PostgreSQL — `NUMERIC(12,2)` for money, never float |
| Migrations | Flyway (`classpath:db/migration`, V1–V12 applied, next is V13) |
| ORM | Spring Data JPA + Hibernate (`ddl-auto: validate` — schema is Flyway-owned, never Hibernate-managed) |
| Auth | JWT via jjwt 0.12.5 — `userId` + `familyIds` claims, 15-min access token, 7-day refresh via `X-Refresh-Token` header |
| Tenant population | `JwtAuthenticationFilter` extracts userId/familyIds and calls `TenantContext.setCurrentTenant()`. `TenantContextInterceptor` **clears** TenantContext in `afterCompletion()` — it does not populate it. |
| Security | Spring Security stateless. Public: `/api/auth/**`, `/api/health`, `/actuator/health`. Everything else requires `Authorization: Bearer <token>`. |
| Password hashing | BCrypt |
| Boilerplate | Lombok 1.18.38 (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`) |
| Observability | Sentry (`sentry-spring-boot-starter-jakarta` 7.3.0) |
| Deployment | Railway — `railway.toml` + `Dockerfile`. Auto-deploys on push to `main`. |

**Planned but not yet in pom.xml:** `bucket4j` for rate limiting — add before first real users.

---

## 3. Dev Setup

```bash
# Requires: Java 17, PostgreSQL running locally
# Create DB: createdb family_finance_dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests (H2 in-memory)
mvn test
```

**Active profile:** `dev` uses `application-dev.yml` — localhost PostgreSQL, Sentry disabled. `DebugController` is `@Profile("dev")` only.

**Production environment variables (Railway):**

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | DB user |
| `DATABASE_PASSWORD` | DB password |
| `JWT_SECRET` | HMAC-SHA256 key (min 256 bits) |
| `SENTRY_DSN` | Sentry project DSN |
| `PORT` | HTTP port (Railway injects this; defaults to 8080) |

---

## 4. Key Source Paths

```
src/main/java/com/family/finance/
├── controller/       AuthController, AccountController, CategoryController,
│                     TransactionController, BudgetController, FamilyController,
│                     ImportController, SyncController, HealthCheckController,
│                     DebugController (@Profile("dev"))
├── entity/           User, Family, FamilyMember, FamilyInvite, Account, Category,
│                     Transaction, Budget, SyncConflict
├── repository/       Spring Data JPA — all queries filter by tenant
├── service/          SyncService, FamilyService
├── sync/             SyncTableHandler (interface) + handlers per entity + SyncConflictLogger
├── security/         SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter,
│                     UserDetailsServiceImpl, TenantContext
├── config/           TenantContextInterceptor, WebMvcConfig
└── dto/              LoginRequest, RegisterRequest, TokenResponse,
                      sync/* (SyncRequest, SyncResponse, SyncTableChanges),
                      family/* (CreateInviteResponse, JoinFamilyRequest, ...)

src/main/resources/
├── application.yml        Production config (env vars)
├── application-dev.yml    Local dev (localhost postgres, Sentry off)
└── db/migration/          V1–V12 applied

src/test/java/com/family/finance/
├── AuthSmokeTest.java
└── SyncRoundTripTest.java
```

---

## 5. Current Phase Status (April 20, 2026)

| Phase | Status | Remaining |
|---|---|---|
| 1 — Foundation | ✅ Complete | Rate limiting (bucket4j) + request size limits still missing |
| 3 — Sync | ✅ Complete | Mobile integration pending (blocked on mobile Phase 2) |
| 4 — Family Sharing | ✅ Mostly complete | ❌ Email verification · ❌ Password reset · ❌ Refresh token revocation |
| 6 — CSV Import | ✅ Mostly complete | ❌ `DELETE /api/users/me` (GDPR) |
| 8 — Web API | ❌ Not started | Blocked until Phase 7 mobile launch gate is passed |

**Immediate priorities (in order):**
1. Email verification + password reset (Phase 4 — required for app store submission)
2. `DELETE /api/users/me` GDPR purge (Phase 6 — required for app store submission)
3. CORS configuration (required before Phase 8 web app)

---

## 6. Reference Files — Read On Demand

| Working on... | Read |
|---|---|
| Database schema, migration rules, entity golden rules | `docs/agents/db-schema.md` |
| Any API endpoint (shapes, status codes, params) | `docs/agents/api-reference.md` |
| Sync wire format, conflict detection, adding a sync handler | `docs/agents/sync-protocol.md` |
| Writing a new entity, repository query, or endpoint | `docs/agents/authoring-rules.md` |
| Checking if a feature is in scope | `docs/agents/non-goals.md` |
| Writing tests or marking a task done | `docs/agents/testing.md` |
| Committing or tagging a release | `docs/agents/git-strategy.md` |
| Cross-repo contracts (money encoding, sync wire format, auth tokens) | `wealthflow-master/CLAUDE.md §4` |

---

## Quick Reference

| Thing | Path |
|---|---|
| Master plan (source of truth) | `wealthflow-master/wealthflow-master-development-plan.md` |
| Master orchestration brief | `wealthflow-master/CLAUDE.md` |
| Frontend agent brief | `wealthflow-master/wealthflow/AGENTS.md` |
| Production config | `src/main/resources/application.yml` |
| Dev config | `src/main/resources/application-dev.yml` |
| Flyway migrations | `src/main/resources/db/migration/` |
| Sync handlers | `src/main/java/com/family/finance/sync/` |
| JWT provider | `src/main/java/com/family/finance/security/JwtTokenProvider.java` |
| Tenant context | `src/main/java/com/family/finance/security/TenantContext.java` |
| Family business logic | `src/main/java/com/family/finance/service/FamilyService.java` |
| Integration tests | `src/test/java/com/family/finance/` |
