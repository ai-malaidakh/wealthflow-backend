# Testing Strategy & Verification Protocol

> Read this before writing tests or marking any task "done".

## Testing Strategy

**Framework:** JUnit 5 + Spring Boot Test + H2 (in-memory, test scope).

### Three Layers

**1. Unit tests — pure logic, no Spring context:**
- JWT generation/validation in `JwtTokenProvider`
- Conflict version comparison logic
- CSV parsing utilities (extract from `ImportController` to a helper class if adding unit tests)

**2. Integration tests (`@SpringBootTest`) — Spring context with H2:**
- Auth flow: register → login → refresh → protected endpoint
- Sync round-trip: push records → pull → verify field-for-field
- Tenant isolation: user A cannot access user B's records — **one test per entity type**
- Family flow: create invite → join → verify membership → remove member

**Key existing test files:**
- `AuthSmokeTest.java` — register + login + refresh flow
- `SyncRoundTripTest.java` — push a record, pull it back

**3. Smoke tests against Railway dev (manual, before each deploy tag):**
- `GET /api/health` returns 200
- Register + login + sync round-trip on real PostgreSQL

### What Not to Test
- Flyway migration correctness (Flyway validates on startup)
- Spring Security filter chain wiring (covered by integration tests)
- JPA relationship mappings (covered implicitly by integration tests)

---

## Verification Protocol

### For new endpoints
1. `mvn test` passes — all existing tests green.
2. New integration test written and passing.
3. Cross-tenant test: user from a different family gets 403 or 404 (never 200).
4. Manually verified with curl or Postman against local dev.

### For sync work
1. Push a record, pull it back — verify all fields round-trip correctly.
2. Push same record from two logical "devices" — verify conflict logged in `sync_conflicts`.
3. Delete on one side, update on other — verify delete wins (latch).

### For database migrations
1. File is `V{n+1}__description.sql` — never edit an applied migration.
2. `mvn spring-boot:run -Dspring-boot.run.profiles=dev` — Flyway validates on startup; failure = wrong migration.
3. Comment non-trivial rollback strategy at the top of the SQL file.

### Pre-push checklist
1. `mvn clean verify` passes.
2. `DebugController` has `@Profile("dev")` — confirmed April 17, 2026.
3. No `application-dev.yml` values (hardcoded localhost, dev secrets) leaked into `application.yml`.
4. No new endpoint is missing tenant isolation.
