# WealthFlow Backend — Agent Brief

> **Scope:** Everything in `wealthflow-backend/`. Backend phases of the master plan.
> **Derived from:** `wealthflow-master-development-plan.md`. If this file and the master plan disagree, the master plan wins.
> **Orchestration layer:** For tasks spanning both repos, defer to `wealthflow-master/CLAUDE.md`.
> **Last synced with master plan:** May 5, 2026.

---

# 12-rules

These rules apply to every task in this project unless explicitly overridden.
Bias: caution over speed on non-trivial work. Use judgment on trivial tasks.

## Rule 1 — Think Before Coding
State assumptions explicitly. If uncertain, ask rather than guess.
Present multiple interpretations when ambiguity exists.
Push back when a simpler approach exists.
Stop when confused. Name what's unclear.

## Rule 2 — Simplicity First
Minimum code that solves the problem. Nothing speculative.
No features beyond what was asked. No abstractions for single-use code.
Test: would a senior engineer say this is overcomplicated? If yes, simplify.

## Rule 3 — Surgical Changes
Touch only what you must. Clean up only your own mess.
Don't "improve" adjacent code, comments, or formatting.
Don't refactor what isn't broken. Match existing style.

## Rule 4 — Goal-Driven Execution
Define success criteria. Loop until verified.
Don't follow steps. Define success and iterate.
Strong success criteria let you loop independently.

## Rule 5 — Use the model only for judgment calls
Use me for: classification, drafting, summarization, extraction.
Do NOT use me for: routing, retries, deterministic transforms.
If code can answer, code answers.

## Rule 6 — Token budgets are not advisory
Per-task: 4,000 tokens. Per-session: 30,000 tokens.
If approaching budget, summarize and start fresh.
Surface the breach. Do not silently overrun.

## Rule 7 — Surface conflicts, don't average them
If two patterns contradict, pick one (more recent / more tested).
Explain why. Flag the other for cleanup.
Don't blend conflicting patterns.

## Rule 8 — Read before you write
Before adding code, read exports, immediate callers, shared utilities.
"Looks orthogonal" is dangerous. If unsure why code is structured a way, ask.

## Rule 9 — Tests verify intent, not just behavior
Tests must encode WHY behavior matters, not just WHAT it does.
A test that can't fail when business logic changes is wrong.

## Rule 10 — Checkpoint after every significant step
Summarize what was done, what's verified, what's left.
Don't continue from a state you can't describe back.
If you lose track, stop and restate.

## Rule 11 — Match the codebase's conventions, even if you disagree
Conformance > taste inside the codebase.
If you genuinely think a convention is harmful, surface it. Don't fork silently.

## Rule 12 — Fail loud
"Completed" is wrong if anything was skipped silently.
"Tests pass" is wrong if any were skipped.
Default to surfacing uncertainty, not hiding it.

---

## App

Spring Boot API serving the React Native mobile app and future React/Vite web app. Separate Git remote — never merged into the frontend monorepo.

Not in scope: mobile app code (`wealthflow/apps/mobile/`), web app, design tokens, shared TypeScript types.

## Tech Stack (locked — escalate before changing)

| Layer | Choice |
|---|---|
| Framework | Spring Boot 3.2.3 |
| Language | Java 17 |
| Build | Maven (`pom.xml`) |
| Database | PostgreSQL |
| Migrations | Flyway (`ddl-auto: validate` — schema is Flyway-owned, never Hibernate) |
| ORM | Spring Data JPA + Hibernate |
| Auth | JWT (jjwt 0.12.5) — `userId` + `familyIds` claims, 15-min access, 7-day refresh via `X-Refresh-Token` header |
| Tenant isolation | `JwtAuthenticationFilter` populates `TenantContext`; `TenantContextInterceptor` **clears** it in `afterCompletion()` (does not populate) |
| Security | Spring Security stateless. Public: `/api/auth/**`, `/api/health`, `/actuator/health` |
| Password | BCrypt |
| Boilerplate | Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`) |
| Observability | Sentry (`sentry-spring-boot-starter-jakarta` 7.3.0) |
| Deployment | Railway — auto-deploys on push to `main` |

**Critical:** Money fields are `NUMERIC(12,2)` / `BigDecimal` everywhere — never float or double. Details → `wealthflow-master/CLAUDE.md §4`.

**Planned but not yet in pom.xml:** `bucket4j` for rate limiting.

## Dev

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # requires Java 17 + local PostgreSQL (family_finance_dev)
mvn test                                               # H2 in-memory
```

Deployment config, env vars, profiles → `docs/agents/deploy.md`

## Status

Phases 1, 3, 4 (mostly), 6 (mostly) complete.

**Remaining before app store submission:** email verification, password reset, refresh token revocation (Phase 4); `DELETE /api/users/me` GDPR purge (Phase 6). CORS configuration required before Phase 8 web app.

→ Full phase status: `wealthflow-master/CLAUDE.md §3`

## Key Paths

```
src/main/java/com/family/finance/
  controller/    Auth, Account, Category, Transaction, Budget, Family, Import, Sync, Health, Debug(@Profile("dev"))
  entity/        User, Family, FamilyMember, FamilyInvite, Account, Category, Transaction, Budget, SyncConflict
  repository/    Spring Data JPA — all queries filter by tenant
  service/       SyncService, FamilyService
  sync/          SyncTableHandler interface + per-entity handlers + SyncConflictLogger
  security/      SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl, TenantContext
  config/        TenantContextInterceptor, WebMvcConfig
  dto/           Request/response DTOs, sync/*, family/*

src/main/resources/
  application.yml       Production config (env vars)
  application-dev.yml   Local dev
  db/migration/         Flyway V1–V15 applied (next: V16)

src/test/java/          AuthSmokeTest, SyncRoundTripTest
```

## Reference Docs (load on demand before working in that area)

| Topic | Doc |
|---|---|
| API endpoints (shapes, status codes) | `docs/agents/api-reference.md` |
| Authoring entities / repositories / endpoints | `docs/agents/authoring-rules.md` |
| Cross-repo contracts (sync, auth, money) | `wealthflow-master/CLAUDE.md §4` |
| DB schema / migrations / entity rules | `docs/agents/db-schema.md` |
| Deployment / Railway / env vars | `docs/agents/deploy.md` |
| Entity audit pattern (@PreUpdate/version/deletedAt) | `src/main/java/com/family/finance/entity/AGENTS.md` |
| Feature scope / non-goals | `docs/agents/non-goals.md` |
| Git / branching | `docs/agents/git-strategy.md` |
| JWT flow / TenantContext / public routes | `src/main/java/com/family/finance/security/AGENTS.md` |
| Sync handlers / conflict resolution | `docs/agents/sync-protocol.md`, `src/main/java/com/family/finance/sync/AGENTS.md` |
| Testing | `docs/agents/testing.md` |

## Commits

Format: `<type>(<scope>): <description>`
Types: `feat` · `fix` · `refactor` · `chore` · `test` · `docs` · `perf` · `db`
Scopes: `auth` · `sync` · `accounts` · `categories` · `transactions` · `budgets` · `family` · `import` · `security` · `db` · `ci` · `deps`

Commit after every logical unit. **Push to `main` after committing** — Railway only deploys what's on GitHub. Full rules: `docs/agents/git-strategy.md`.
