# WealthFlow Backend — Agent Brief

> **What this is:** A self-contained instruction set for any agent working on the WealthFlow backend. Read this before writing code.
> **Derived from:** `/Users/ai.assistant/Documents/wealthflow-master-development-plan.md` (the master plan). If this doc and the master plan disagree, the master plan wins — file a diff and reconcile.
> **Last synced with master plan:** April 17, 2026.

---

## 1. Identity & Scope

**Product name:** WealthFlow. This repo (`wealthflow-backend`) is the Spring Boot API that serves both the React Native mobile app and the future React/Vite web app. It lives in a separate GitHub remote — never merged into the `wealthflow` frontend monorepo.

**What this agent does:** Everything in `wealthflow-backend/`. Backend phases of the master plan — auth, CRUD, sync, family sharing, CSV import, and Phase 8 web API additions.

**What this agent does not do:**
- Mobile app code — that's `wealthflow/apps/mobile/` in the frontend repo.
- Web app code — that's `wealthflow/apps/web/` (Phase 8, not yet built).
- Design tokens, shared TypeScript types — those are `wealthflow/packages/` and are **not consumed by the backend**.

**Current frontend dependency status:**
- Phase 3 sync is **LIVE** — mobile can call `/api/sync/pull` and `/api/sync/push`.
- Phase 4 family sharing is **LIVE** — invite flow, join, member list, role update, remove member all work.
- Phase 6 CSV import is **LIVE** — `POST /api/import/coinkeeper` works.
- **What remains blocking mobile:** email verification, password reset (Phase 4 Week 9), and GDPR deletion endpoint (Phase 6).

---

## 2. Locked Tech Stack

These are not up for debate. If you find a reason to change one, stop and escalate.

| Layer | Choice |
|---|---|
| Framework | Spring Boot 3.2.3 |
| Language | Java 17 |
| Build | Maven (`pom.xml`) |
| Database | PostgreSQL — `NUMERIC(12,2)` for money, never float |
| Migrations | Flyway (`classpath:db/migration`, V1–V12 applied) |
| ORM | Spring Data JPA + Hibernate (`ddl-auto: validate` — schema is Flyway-owned, never Hibernate-managed) |
| Auth | JWT via jjwt 0.12.5 — `userId` + `familyIds` claims, 15-min access token, 7-day refresh token passed as `X-Refresh-Token` header |
| Tenant population | `JwtAuthenticationFilter` extracts `userId`/`familyIds` from every JWT and calls `TenantContext.setCurrentTenant()`. `TenantContextInterceptor` **clears** TenantContext in `afterCompletion()` — it does not populate it |
| Security | Spring Security stateless. Public: `/api/auth/**`, `/api/health`, `/actuator/health`. Everything else requires `Authorization: Bearer <token>` |
| Password hashing | BCrypt |
| Conflict resolution | `version` column on every mutable entity (auto-incremented by `@PreUpdate`). Push: if `clientVersion < serverVersion` → log to `sync_conflicts`, server wins. Delete is a one-way latch (`deleted_at` once set cannot be cleared) |
| Observability | Sentry (`sentry-spring-boot-starter-jakarta` 7.3.0) |
| Boilerplate | Lombok 1.18.38 (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`) |
| Deployment | Railway — `railway.toml` + `Dockerfile` in repo root. Auto-deploys on push to `main` |

**Not yet in pom.xml but planned:**
- `bucket4j` for rate limiting — add before first real users (Phase 4 hardening)

---

## 3. Dev Setup

**Run locally:**
```bash
# Requires: Java 17, PostgreSQL running locally
# Create DB: createdb family_finance_dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Active profile:** `dev` uses `application-dev.yml` — points to `localhost:5432/family_finance_dev`, disables Sentry. `DebugController` is only active under the `dev` profile (`@Profile("dev")`).

**Environment variables (production / Railway):**
| Variable | Purpose |
|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | DB user |
| `DATABASE_PASSWORD` | DB password |
| `JWT_SECRET` | HMAC-SHA256 key (min 256 bits) |
| `SENTRY_DSN` | Sentry project DSN |
| `PORT` | HTTP port (Railway injects this; defaults to 8080) |

**Run tests:**
```bash
mvn test
# Tests use H2 in-memory DB
```

**Key test files:**
- `AuthSmokeTest.java` — register + login + refresh flow
- `SyncRoundTripTest.java` — push a record, pull it back

---

## 4. Repo Structure

```
wealthflow-backend/
├── pom.xml
├── Dockerfile
├── railway.toml
├── src/
│   ├── main/
│   │   ├── java/com/family/finance/
│   │   │   ├── FinanceApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java           ✅ POST /api/auth/register|login|refresh
│   │   │   │   ├── AccountController.java         ✅ GET|POST|PUT|DELETE /api/accounts
│   │   │   │   ├── CategoryController.java        ✅ GET|POST|PUT|DELETE /api/categories
│   │   │   │   ├── TransactionController.java     ✅ GET|POST|PUT|DELETE /api/transactions (+ /account/{accountId})
│   │   │   │   ├── BudgetController.java          ✅ GET|POST|PUT|DELETE /api/budgets (+ /category/{categoryId})
│   │   │   │   ├── FamilyController.java          ✅ /api/families — invite, join, list members, update role, remove
│   │   │   │   ├── ImportController.java          ✅ POST /api/import/coinkeeper
│   │   │   │   ├── SyncController.java            ✅ GET /api/sync/pull, POST /api/sync/push
│   │   │   │   ├── HealthCheckController.java     ✅ GET /api/health
│   │   │   │   └── DebugController.java           ⚠️  @Profile("dev") only — GET /debug/sentry-test
│   │   │   ├── entity/
│   │   │   │   ├── User.java             ✅ implements UserDetails; @PreUpdate bumps version + updatedAt
│   │   │   │   ├── Family.java           ✅
│   │   │   │   ├── FamilyMember.java     ✅ Role: ADMIN | MEMBER
│   │   │   │   ├── FamilyInvite.java     ✅ code VARCHAR(12), expires_at, used_at, used_by
│   │   │   │   ├── Account.java          ✅ AccountType: CHECKING|SAVINGS|CREDIT|CASH|INVESTMENT
│   │   │   │   ├── Category.java         ✅ CategoryType: INCOME|EXPENSE
│   │   │   │   ├── Transaction.java      ✅ amount NUMERIC(12,2), import_hash for dedup
│   │   │   │   ├── Budget.java           ✅ period_start/end as LocalDate
│   │   │   │   └── SyncConflict.java     ✅ client_data/server_data as JSONB
│   │   │   ├── repository/               (Spring Data JPA — all queries filter by tenant)
│   │   │   ├── service/
│   │   │   │   ├── SyncService.java      ✅ push + pull, pluggable handler dispatch
│   │   │   │   └── FamilyService.java    ✅ invite create/consume, member CRUD, admin checks
│   │   │   ├── sync/
│   │   │   │   ├── SyncTableHandler.java      ✅ interface
│   │   │   │   ├── AccountSyncHandler.java    ✅
│   │   │   │   ├── CategorySyncHandler.java   ✅
│   │   │   │   ├── TransactionSyncHandler.java ✅
│   │   │   │   ├── BudgetSyncHandler.java     ✅
│   │   │   │   └── SyncConflictLogger.java    ✅
│   │   │   ├── security/
│   │   │   │   ├── SecurityConfig.java              ✅
│   │   │   │   ├── JwtTokenProvider.java            ✅
│   │   │   │   ├── JwtAuthenticationFilter.java     ✅ populates TenantContext from JWT
│   │   │   │   ├── UserDetailsServiceImpl.java      ✅
│   │   │   │   └── TenantContext.java               ✅ ThreadLocal userId + familyIds
│   │   │   ├── config/
│   │   │   │   ├── TenantContextInterceptor.java    ✅ clears TenantContext in afterCompletion()
│   │   │   │   └── WebMvcConfig.java                ✅
│   │   │   └── dto/
│   │   │       ├── LoginRequest.java, RegisterRequest.java, TokenResponse.java
│   │   │       ├── sync/   SyncRequest.java, SyncResponse.java, SyncTableChanges.java
│   │   │       └── family/ CreateInviteResponse, JoinFamilyRequest, JoinFamilyResponse,
│   │   │                   FamilyMemberDto, UpdateMemberRoleRequest
│   │   └── resources/
│   │       ├── application.yml       (production — env vars)
│   │       ├── application-dev.yml   (local dev — localhost postgres, Sentry disabled)
│   │       └── db/migration/         (V1–V12 applied — see §5)
│   └── test/
│       └── java/com/family/finance/
│           ├── AuthSmokeTest.java
│           └── SyncRoundTripTest.java
```

---

## 5. Database Schema (Applied Migrations V1–V12)

**Golden rules:**
- Money is `NUMERIC(12,2)` in PostgreSQL. Never `FLOAT` or `DOUBLE`.
- Every mutable table has `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`, `deleted_at TIMESTAMPTZ` (soft delete), and `version BIGINT`.
- `deleted_at` is a one-way latch — once set, it cannot be cleared by a sync push or any normal code path.
- IDs are `UUID`. Client-generated UUIDs are accepted on sync push (idempotent create).
- Tenant ownership on `accounts` and `categories`: exactly one of `family_id` or `user_id` is non-null, enforced by a DB CHECK constraint.
- `@PreUpdate` on every entity auto-increments `version` and sets `updatedAt`. **Every new entity must have this hook** — sync conflict detection depends on it.

**Applied migrations:**

| Version | File | What it does |
|---|---|---|
| V1 | `V1__create_users.sql` | `users` table |
| V2 | `V2__create_families_and_members.sql` | `families` + `family_members` tables |
| V3 | `V3__create_accounts.sql` | `accounts` table with ownership CHECK constraint |
| V4 | `V4__create_audit_columns.sql` | Audit columns backfilled |
| V5 | `V5__create_categories.sql` | `categories` table |
| V6 | `V6__create_transactions.sql` | `transactions` table (`amount NUMERIC(12,2)`, `currency CHAR(3)` default `USD`) |
| V7 | `V7__create_budgets.sql` | `budgets` table |
| V8 | `V8__create_sync_conflicts.sql` | `sync_conflicts` table (JSONB client_data/server_data, resolution enum) |
| V9 | `V9__fix_currency_column_type.sql` | Currency column type fix |
| V10 | `V10__add_family_members_audit_columns.sql` | Audit columns on `family_members` |
| V11 | `V11__add_import_hash_to_transactions.sql` | `import_hash` column on `transactions` (SHA-256 dedup for CSV import) |
| V12 | `V12__create_family_invites.sql` | `family_invites` — code VARCHAR(12), expires_at, used_at, used_by |

**Next migration number:** `V13__...`

---

## 6. API Reference (All Live Endpoints)

All endpoints (except health + auth) require `Authorization: Bearer <accessToken>`.

### Auth — `/api/auth`
```
POST /api/auth/register
  Body: { email, password, displayName, familyName }
  Returns 201: { accessToken, refreshToken, expiresIn, userId, familyIds }

POST /api/auth/login
  Body: { email, password }
  Returns 200: { accessToken, refreshToken, expiresIn, userId, familyIds }

POST /api/auth/refresh
  Header: X-Refresh-Token: <refreshToken>
  Returns 200: { accessToken, refreshToken, expiresIn, userId, familyIds }
```

### Accounts — `/api/accounts`
```
GET    /api/accounts              → all accounts for caller's family + personal
GET    /api/accounts/{id}         → single account (tenant-scoped, 404 if not owned)
POST   /api/accounts              Body: { name, type, balance, currency, familyId? }  → 201
PUT    /api/accounts/{id}         Body: same as POST
DELETE /api/accounts/{id}         → soft delete (sets deleted_at), 204
```

### Categories — `/api/categories`
```
GET    /api/categories            → all categories for caller's family + personal
GET    /api/categories/{id}
POST   /api/categories            Body: { name, type (INCOME|EXPENSE), familyId? }  → 201
PUT    /api/categories/{id}
DELETE /api/categories/{id}       → soft delete, 204
```

### Transactions — `/api/transactions`
```
GET    /api/transactions                   → all transactions visible to caller's tenant
GET    /api/transactions/{id}
GET    /api/transactions/account/{accountId}  → filtered by account (verifies account ownership)
POST   /api/transactions          Body: { accountId, categoryId?, amount, description?, date (YYYY-MM-DD) }  → 201
PUT    /api/transactions/{id}     Body: same as POST
DELETE /api/transactions/{id}     → soft delete, 204
```

### Budgets — `/api/budgets`
```
GET    /api/budgets                         → all budgets for caller's families
GET    /api/budgets/{id}
GET    /api/budgets/category/{categoryId}   → filtered by category
POST   /api/budgets               Body: { familyId, categoryId, amount, currency?, periodStart, periodEnd }  → 201
PUT    /api/budgets/{id}          Body: same as POST (familyId must match existing budget)
DELETE /api/budgets/{id}          → soft delete, 204
```

### Family — `/api/families`
```
POST   /api/families/{familyId}/invites
  Auth: caller must be ADMIN of familyId
  Returns 201: { id, code, expiresAt }
  Note: code is 8-char uppercase alphanumeric, expires in 7 days

POST   /api/families/join
  Body: { code }
  Returns 201: { familyId, familyName, role }
  Note: marks invite used_at + used_by; 409 if already a member

GET    /api/families/{familyId}/members
  Auth: caller must be a member of familyId
  Returns: [{ id, userId, email, displayName, role, joinedAt }]

PATCH  /api/families/{familyId}/members/{memberId}/role
  Auth: caller must be ADMIN
  Body: { role: "ADMIN"|"MEMBER" }
  Returns: FamilyMemberDto

DELETE /api/families/{familyId}/members/{memberId}
  Auth: caller must be ADMIN. Cannot remove self. Cannot remove last admin.
  Returns 204
```

### Sync — `/api/sync`
```
GET  /api/sync/pull?lastPulledAt=<epoch_ms>&schemaVersion=1
  Returns: {
    changes: {
      accounts:     { created: [...], updated: [...], deleted: ["uuid", ...] },
      categories:   { created: [...], updated: [...], deleted: ["uuid", ...] },
      transactions: { created: [...], updated: [...], deleted: ["uuid", ...] },
      budgets:      { created: [...], updated: [...], deleted: ["uuid", ...] }
    },
    timestamp: <epoch_ms>
  }
  Note: omitting lastPulledAt triggers full sync (returns all records since epoch).

POST /api/sync/push?lastPulledAt=<epoch_ms>
  Body: { changes: { <table>: { created: [...], updated: [...], deleted: ["uuid", ...] } } }
  Returns: { timestamp: <epoch_ms> }
```

### Import — `/api/import`
```
POST /api/import/coinkeeper
  Content-Type: multipart/form-data
  Params: file (CSV file, max 10MB), accountId (UUID query param)
  CSV required columns: date (YYYY-MM-DD), amount, category, description, account
  Returns 200: { imported, duplicates, errors: [...] }
  Note: dedup via SHA-256(date + amount + description) stored in import_hash.
        Categories are auto-created as EXPENSE type if not found.
```

### Health
```
GET /api/health   → { status: "UP", timestamp: "..." }  (no auth required)
```

### Endpoints not yet built
```
POST   /api/auth/forgot-password          ❌  Phase 4 — email with reset token
POST   /api/auth/reset-password           ❌  Phase 4 — consume token, set new password
DELETE /api/users/me                      ❌  Phase 6 — hard purge all user data (GDPR)

# Phase 8 web additions (not needed until web app is built):
GET /api/transactions?page=&size=&sort=   ❌  pagination for web data table
GET /api/transactions?from=&to=&categoryId=&q=  ❌  advanced filters
GET /api/reports/monthly-summary          ❌
GET /api/reports/category-trend           ❌
POST /api/transactions/{id}/split         ❌
POST /api/budgets/from-template           ❌
```

---

## 7. Sync Protocol Details

The sync protocol follows WatermelonDB's exact push/pull contract. The mobile `syncEngine.ts` calls **push first, then pull.**

### Wire format
Every record in `created` / `updated` arrays uses **snake_case** keys matching WatermelonDB column names exactly:

```json
{
  "id": "uuid-string",
  "name": "Groceries",
  "type": "EXPENSE",
  "family_id": "uuid-string",
  "user_id": null,
  "created_at": 1712345678000,
  "updated_at": 1712345678000,
  "deleted_at": null,
  "version": 3
}
```

**Money fields:** Backend stores `NUMERIC(12,2)` and serializes as a decimal string (`"balance": "1234.56"`). WatermelonDB stores integer cents (`balance_cents`). **The multiply/divide-by-100 conversion lives on the mobile side** — the backend does not convert. See `wealthflow/AGENTS.md §2.6` for the mobile schema.

**Timestamp fields:** Serialized as epoch milliseconds (long integers).

### Conflict detection (server wins)
When a push arrives where `clientVersion < serverVersion`:
1. Both snapshots written to `sync_conflicts` (JSONB).
2. Client changes **not applied** — server version wins.
3. Record returned in next pull with server-version data.
4. Mobile shows a conflict badge. Resolution UI is web-only (Phase 8).

### Adding a new syncable table
1. Implement `SyncTableHandler` with `tableName()` matching the WatermelonDB model's table name.
2. Annotate `@Component` — `SyncService` auto-discovers all beans.
3. Implement `applyPush()` (version check + delete latch + conflict log) and `buildPull()`.
4. No changes needed to `SyncController` or `SyncService`.

---

## 8. Tenant Isolation Rules

**Never skip these.** Cross-family data leakage is a critical security failure.

1. `JwtAuthenticationFilter` populates `TenantContext` (userId + familyIds) from every JWT. `TenantContextInterceptor` clears it after the request completes.
2. All repository queries **must** filter by `TenantContext.getCurrentFamilyIds()` and/or `TenantContext.getCurrentUserId()`. Follow the `findByIdAndTenant(id, familyIds, userId)` pattern established in `AccountRepository`, `CategoryRepository`, and `TransactionRepository`.
3. Family membership check before any family-scoped write: `TenantContext.getCurrentFamilyIds().contains(request.familyId())`. Throw 403 if not a member.
4. For admin-only operations: use `FamilyService.requireAdminMembership(familyId)` pattern — check `FamilyMember.Role.ADMIN`, throw 403 if not admin.
5. Integration test requirement: for every new entity, write a test proving user A cannot read, update, or delete user B's records.

---

## 9. Entity Authoring Rules

Every new JPA entity **must** follow this pattern or sync conflict detection breaks:

```java
@Column(nullable = false)
private Instant updatedAt = Instant.now();

@Column
private Instant deletedAt;

@Column(nullable = false)
private long version = 0;

@PreUpdate
void onUpdate() {
    updatedAt = Instant.now();
    version++;   // ← REQUIRED: sync conflict detection reads this
}
```

- Never use Hibernate `ddl-auto: create` or `update` — all schema changes go through a new Flyway migration.
- Set IDs via `entity.setId(UUID.randomUUID())` on creation, not auto-generated sequences. This allows client-generated IDs from sync push.
- Money fields: `BigDecimal`, `@Column(precision = 12, scale = 2)`. Accept from wire as String or Number, convert via `new BigDecimal(value.toString())` — never cast to float.

---

## 10. Phase Status

### Phase 1 — ✅ Complete
Auth, core entities, Flyway V1–V9, account + category CRUD, deployed to Railway, Sentry integrated.

**Remaining gap from original plan:**
- Rate limiting (`bucket4j`) — not yet in `pom.xml`. Add before production load.
- Request size limits — not configured in `application.yml`.

### Phase 3 — ✅ Complete (server-side)
`SyncController`, `SyncService`, handlers for `accounts`, `categories`, `transactions`, `budgets`. `sync_conflicts` table (V8). Mobile can sync.

### Phase 4 — ✅ Mostly Complete
- ✅ `FamilyController` + `FamilyService` — invite create, join, list members, update role, remove member
- ✅ V12 migration — `family_invites` table
- ✅ Last-admin protection, self-removal guard, invite consumption
- ❌ Email verification on signup — not built (`email_verified` flag doesn't exist on `User` yet; needs V13 migration + verification token table)
- ❌ Password reset flow — not built
- ❌ Refresh token revocation for "remove device" — tokens are stateless JWTs; revocation store not yet added

### Phase 6 — Partially Complete
- ✅ `ImportController` — `POST /api/import/coinkeeper` with SHA-256 dedup, auto-category creation, 10MB file limit
- ❌ `DELETE /api/users/me` — GDPR hard purge not built

### Phase 8 — Not started
Web-specific API additions: pagination, advanced filters, report aggregations. Do not build until Phase 7 mobile launch decision gate is passed.

---

## 11. Adding New Endpoints — Checklist

For every new controller method:

- [ ] Use `TenantContext.getCurrentUserId()` and `TenantContext.getCurrentFamilyIds()` — never trust user-supplied IDs for ownership.
- [ ] Soft delete (set `deleted_at`), not hard delete — unless it is the GDPR purge endpoint.
- [ ] Validate input with `@Valid` + Jakarta constraint annotations on the request record.
- [ ] Throw `ResponseStatusException` with the correct HTTP status — do not let JPA exceptions propagate to the client.
- [ ] Return `ResponseEntity<T>` with explicit status codes (201 for creates, 204 for deletes).
- [ ] New entity follows the `@PreUpdate` / `version` / `deletedAt` pattern from §9.
- [ ] Write an integration test asserting cross-tenant access is denied (403 or 404).
- [ ] New Flyway migration is `V{n+1}__description.sql` — never edit an applied migration.

---

## 12. What's Intentionally Deferred

These are real features. Do not build them until their phase arrives.

- ❌ **Rate limiting (bucket4j)** — add before production load, not blocking MVP.
- ❌ **Server-side refresh token revocation** — currently stateless JWTs. A revocation table is needed for "remove device". Build with email verification in Phase 4 hardening.
- ❌ **Email verification + password reset** — V13 schema needed. Build before app store submission.
- ❌ **GDPR hard purge** (`DELETE /api/users/me`) — Phase 6 remaining item.
- ❌ **Multi-currency conversion** — `currency` column exists; conversion logic is v2.
- ❌ **Bank API / Plaid** — v2.
- ❌ **AI categorization** — v2.
- ❌ **Recurring transaction auto-generation** — Phase 8D.
- ❌ **Push notification service** — Phase 7+.
- ❌ **Row-Level Security (PostgreSQL RLS)** — recommended hardening layer post-launch, on top of existing CHECK constraints + tenant query filters.
- ❌ **CORS configuration** — not yet configured. Required before any browser-based client (Phase 8 web app) can call the API. Add `@CrossOrigin` or a `CorsConfigurationSource` bean before Phase 8.

---

## 13. Testing Strategy

**Framework:** JUnit 5 + Spring Boot Test + H2 (in-memory, test scope).

**Three layers:**

**1. Unit tests** — pure logic, no Spring context:
- JWT generation/validation in `JwtTokenProvider`
- Conflict version comparison logic
- CSV parsing utilities (already in `ImportController` — extract to a helper class if adding unit tests)

**2. Integration tests (`@SpringBootTest`)** — spin up Spring context with H2:
- Auth flow: register → login → refresh → protected endpoint
- Sync round-trip: push records → pull → verify field-for-field
- Tenant isolation: user A cannot access user B's records — **one test per entity type**
- Family flow: create invite → join → verify membership → remove member

**3. Smoke tests against Railway dev environment (manual):**
- `GET /api/health` returns 200
- Register + login + sync round-trip on real PostgreSQL
- Run before each deploy tag

**What not to test:**
- Flyway migration correctness (Flyway validates on startup)
- Spring Security filter chain wiring (test via integration test)
- JPA relationship mappings (covered by integration tests implicitly)

---

## 14. Git & Commit Strategy

**Branching:**
- `main` is the only permanent branch — Railway deploys from it. **Commit directly to `main`** for all normal work.
- No `develop`, `feature/*`, `release/*`, `hotfix/*`.

**Conventional Commits:** `<type>(<scope>): <description>`

Types: `feat` · `fix` · `refactor` · `chore` · `test` · `docs` · `perf` · `db`

Scopes: `auth` · `sync` · `accounts` · `categories` · `transactions` · `budgets` · `family` · `import` · `security` · `db` · `ci` · `deps`

**Examples:**
```
feat(auth): add forgot-password and reset-password endpoints
feat(db): V13 add email_verification_tokens table
fix(sync): handle null familyIds in AccountSyncHandler pull query
fix(security): gate DebugController behind @Profile("dev")
test(family): assert non-member cannot list family members
chore(deps): add bucket4j for rate limiting
```

**Releases:** SemVer annotated tags:
```bash
git tag v0.4.0 -m "Phase 4 complete — family sharing live"
```

---

## 15. Verification Protocol

Before marking any task "done":

**For new endpoints:**
1. `mvn test` passes — all existing tests green.
2. New integration test written and passing.
3. Cross-tenant test: user from a different family gets 403 or 404 (never 200).
4. Manually verify with curl or Postman against local dev.

**For sync work:**
1. Push a record, pull it back — verify all fields round-trip correctly.
2. Push same record from two logical "devices" — verify conflict logged in `sync_conflicts`.
3. Delete on one side, update on other — verify delete wins (latch).

**For database migrations:**
1. File is `V{n+1}__description.sql` — never edit an applied migration.
2. `mvn spring-boot:run -Dspring-boot.run.profiles=dev` — Flyway validates on startup. If it fails, the migration is wrong.
3. Comment non-trivial rollback strategy at the top of the SQL file.

**Pre-push checklist:**
1. `mvn clean verify` passes.
2. `DebugController` has `@Profile("dev")` — it does as of April 17, 2026.
3. No `application-dev.yml` values (hardcoded localhost, dev secrets) have leaked into `application.yml`.
4. No new endpoint is missing tenant isolation.

---

## 16. Quick Reference — Where to Find Things

| Thing | Path |
|---|---|
| Master plan (source of truth) | `/Users/ai.assistant/Documents/wealthflow-master-development-plan.md` |
| This file | `/Users/ai.assistant/Documents/wealthflow-backend/AGENTS.md` |
| Frontend agent brief | `/Users/ai.assistant/Documents/wealthflow/AGENTS.md` |
| Production config | `src/main/resources/application.yml` |
| Dev config | `src/main/resources/application-dev.yml` |
| Flyway migrations | `src/main/resources/db/migration/` |
| Sync table handlers | `src/main/java/com/family/finance/sync/` |
| JWT provider | `src/main/java/com/family/finance/security/JwtTokenProvider.java` |
| Tenant context | `src/main/java/com/family/finance/security/TenantContext.java` |
| Family business logic | `src/main/java/com/family/finance/service/FamilyService.java` |
| Integration tests | `src/test/java/com/family/finance/` |
