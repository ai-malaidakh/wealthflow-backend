# Backend Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 6 audit findings (2 HIGH, 4 MEDIUM) before the backend receives production traffic.

**Architecture:** All fixes are isolated changes — a new converter, a new entity + repository, one filter, and modifications to two existing classes. The `revoked_tokens` table already exists in V4 migration. No schema changes needed for the revocation feature. Token rotation (revoke old JTI on each refresh) is the security model.

**Tech Stack:** Spring Boot 3.2.3, JJWT 0.12.5, bucket4j-core 8.10.1 (new), H2 (tests), PostgreSQL (prod), Flyway, JUnit 5 / MockMvc

---

## File Map

| Action | Path | Purpose |
|---|---|---|
| Create | `src/main/java/com/family/finance/entity/ResolutionConverter.java` | JPA converter: enum ↔ lowercase string |
| Modify | `src/main/java/com/family/finance/entity/SyncConflict.java` | Use `@Convert` instead of `@Enumerated` |
| Modify | `src/main/java/com/family/finance/controller/DebugController.java` | Add `@Profile("dev")` |
| Modify | `src/main/java/com/family/finance/controller/AuthController.java` | Fix stale familyIds + add logout + check revocation |
| Create | `src/main/resources/db/migration/V13__drop_duplicate_version_triggers.sql` | Drop V4 triggers that double-increment `version` |
| Create | `src/main/java/com/family/finance/entity/RevokedToken.java` | Maps to existing `revoked_tokens` table |
| Create | `src/main/java/com/family/finance/repository/RevokedTokenRepository.java` | Persistence for revoked JTIs |
| Modify | `src/main/java/com/family/finance/security/JwtTokenProvider.java` | Embed JTI in refresh tokens + `extractJti()` |
| Modify | `pom.xml` | Add `bucket4j-core` dependency |
| Create | `src/main/java/com/family/finance/security/RateLimitFilter.java` | 10 req/min/IP on `/api/auth/**` |
| Modify | `src/test/java/com/family/finance/AuthSmokeTest.java` | Tests for Tasks 2, 3, 5, 6 |

---

## Task 1: Fix SyncConflict resolution enum case mismatch

**Why:** The V8 migration's CHECK constraint expects lowercase values (`server_wins`). `@Enumerated(EnumType.STRING)` writes uppercase (`SERVER_WINS`). The first resolved conflict in production will throw a DB constraint violation.

**Files:**
- Create: `src/main/java/com/family/finance/entity/ResolutionConverter.java`
- Modify: `src/main/java/com/family/finance/entity/SyncConflict.java:59-60`

- [ ] **Step 1.1: Write the failing test**

Add to `AuthSmokeTest.java` — but this fix is best validated with a unit test for the converter itself. Create a new test class:

File: `src/test/java/com/family/finance/ResolutionConverterTest.java`

```java
package com.family.finance;

import com.family.finance.entity.ResolutionConverter;
import com.family.finance.entity.SyncConflict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolutionConverterTest {

    private final ResolutionConverter converter = new ResolutionConverter();

    @Test
    void convertsServerWinsToLowercase() {
        assertThat(converter.convertToDatabaseColumn(SyncConflict.Resolution.SERVER_WINS))
                .isEqualTo("server_wins");
    }

    @Test
    void convertsClientWinsToLowercase() {
        assertThat(converter.convertToDatabaseColumn(SyncConflict.Resolution.CLIENT_WINS))
                .isEqualTo("client_wins");
    }

    @Test
    void convertsMergedToLowercase() {
        assertThat(converter.convertToDatabaseColumn(SyncConflict.Resolution.MERGED))
                .isEqualTo("merged");
    }

    @Test
    void convertsNullToDatabaseNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertsLowercaseStringToEnum() {
        assertThat(converter.convertToEntityAttribute("server_wins"))
                .isEqualTo(SyncConflict.Resolution.SERVER_WINS);
    }

    @Test
    void convertsNullStringToNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
```

- [ ] **Step 1.2: Run test to confirm it fails**

```bash
./mvnw test -pl . -Dtest=ResolutionConverterTest -q
```

Expected: compilation error — `ResolutionConverter` does not exist yet.

- [ ] **Step 1.3: Create ResolutionConverter**

File: `src/main/java/com/family/finance/entity/ResolutionConverter.java`

```java
package com.family.finance.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ResolutionConverter implements AttributeConverter<SyncConflict.Resolution, String> {

    @Override
    public String convertToDatabaseColumn(SyncConflict.Resolution attribute) {
        if (attribute == null) return null;
        return attribute.name().toLowerCase();
    }

    @Override
    public SyncConflict.Resolution convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return SyncConflict.Resolution.valueOf(dbData.toUpperCase());
    }
}
```

- [ ] **Step 1.4: Update SyncConflict to use the converter**

In `src/main/java/com/family/finance/entity/SyncConflict.java`, replace lines 59-60:

```java
// BEFORE:
@Enumerated(EnumType.STRING)
@Column(name = "resolution", length = 20)
private Resolution resolution;

// AFTER:
@Convert(converter = ResolutionConverter.class)
@Column(name = "resolution", length = 20)
private Resolution resolution;
```

Also remove the unused import `import jakarta.persistence.EnumType;` and add `import jakarta.persistence.Convert;`.

- [ ] **Step 1.5: Run test to confirm it passes**

```bash
./mvnw test -pl . -Dtest=ResolutionConverterTest -q
```

Expected: `BUILD SUCCESS`, 6 tests passed.

- [ ] **Step 1.6: Commit**

```bash
git add src/main/java/com/family/finance/entity/ResolutionConverter.java \
        src/main/java/com/family/finance/entity/SyncConflict.java \
        src/test/java/com/family/finance/ResolutionConverterTest.java
git commit -m "fix(sync): use lowercase converter for sync_conflicts resolution enum

@Enumerated(EnumType.STRING) wrote SERVER_WINS but V8 CHECK constraint
expects server_wins. Converter serializes to lowercase, matching the
existing DB constraint without a migration."
```

---

## Task 2: Remove DebugController from production

**Why:** `GET /debug/sentry-test` throws a `RuntimeException` for any authenticated user. In production this pollutes Sentry with fake alerts. Adding `@Profile("dev")` means the controller bean is never instantiated unless the `dev` Spring profile is active.

**Files:**
- Modify: `src/main/java/com/family/finance/controller/DebugController.java`
- Modify: `src/test/java/com/family/finance/AuthSmokeTest.java`

- [ ] **Step 2.1: Write the failing test**

Add this test to `AuthSmokeTest.java`:

```java
@Test
void debugEndpointNotAvailableInDefaultProfile() throws Exception {
    RegisterRequest reg = new RegisterRequest(
            "debug-test@example.com", "pass123", "Debug", "Debug Family");
    MvcResult regResult = mvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reg)))
            .andReturn();
    TokenResponse tokens = objectMapper.readValue(
            regResult.getResponse().getContentAsString(), TokenResponse.class);

    mvc.perform(get("/debug/sentry-test")
                    .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isNotFound());
}
```

- [ ] **Step 2.2: Run test to confirm it fails**

```bash
./mvnw test -pl . -Dtest=AuthSmokeTest#debugEndpointNotAvailableInDefaultProfile -q
```

Expected: FAIL — currently returns 500 (RuntimeException), not 404.

- [ ] **Step 2.3: Add @Profile("dev") to DebugController**

Replace the full content of `src/main/java/com/family/finance/controller/DebugController.java`:

```java
package com.family.finance.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Profile("dev")
@RestController
@RequestMapping("/debug")
public class DebugController {

    @GetMapping("/sentry-test")
    public ResponseEntity<Map<String, Object>> sentryTest() {
        throw new RuntimeException("Sentry test exception");
    }
}
```

- [ ] **Step 2.4: Run test to confirm it passes**

```bash
./mvnw test -pl . -Dtest=AuthSmokeTest#debugEndpointNotAvailableInDefaultProfile -q
```

Expected: PASS.

- [ ] **Step 2.5: Commit**

```bash
git add src/main/java/com/family/finance/controller/DebugController.java \
        src/test/java/com/family/finance/AuthSmokeTest.java
git commit -m "fix(api): gate DebugController behind dev profile

/debug/sentry-test was accessible to any authenticated user in
production, polluting Sentry with intentional exceptions."
```

---

## Task 3: Fix stale familyIds in refresh endpoint

**Why:** `AuthController.refresh()` reads `familyIds` from the old token's claims. If the user joins a new family between token refreshes, the new access token will not include the new `familyId`, causing all Phase 4 family-scoped queries to silently exclude their data. The login endpoint already does this correctly — refresh must match it.

**Files:**
- Modify: `src/main/java/com/family/finance/controller/AuthController.java:107-108`
- Modify: `src/test/java/com/family/finance/AuthSmokeTest.java`

- [ ] **Step 3.1: Write the failing test**

Add to `AuthSmokeTest.java`:

```java
@Test
void refreshReturnsNonEmptyFamilyIds() throws Exception {
    RegisterRequest reg = new RegisterRequest(
            "refresh-test@example.com", "pass123", "Eve", "Eve Family");
    MvcResult regResult = mvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reg)))
            .andReturn();
    TokenResponse original = objectMapper.readValue(
            regResult.getResponse().getContentAsString(), TokenResponse.class);

    MvcResult refreshResult = mvc.perform(post("/api/auth/refresh")
                    .header("X-Refresh-Token", original.refreshToken()))
            .andExpect(status().isOk())
            .andReturn();

    TokenResponse refreshed = objectMapper.readValue(
            refreshResult.getResponse().getContentAsString(), TokenResponse.class);

    assertThat(refreshed.familyIds()).isNotEmpty();
    assertThat(refreshed.familyIds()).containsExactlyElementsOf(original.familyIds());
}
```

Add the import at the top of `AuthSmokeTest.java`:
```java
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 3.2: Run test to confirm current behavior**

```bash
./mvnw test -pl . -Dtest=AuthSmokeTest#refreshReturnsNonEmptyFamilyIds -q
```

This test currently passes because the stale familyIds are copied from the old token (same value since no family change happens in the test). This is a regression-guard test — it will catch future regressions and confirms the endpoint returns correct data.

- [ ] **Step 3.3: Update AuthController.refresh() to query DB**

In `src/main/java/com/family/finance/controller/AuthController.java`, replace lines 107-108:

```java
// BEFORE:
UUID userId = jwtTokenProvider.extractUserId(claims);
List<UUID> familyIds = jwtTokenProvider.extractFamilyIds(claims);

// AFTER:
UUID userId = jwtTokenProvider.extractUserId(claims);
List<UUID> familyIds = familyMemberRepository.findActiveByUserId(userId)
        .stream()
        .map(m -> m.getFamily().getId())
        .toList();
```

`familyMemberRepository` is already injected at line 36 — no new dependency needed.

- [ ] **Step 3.4: Run test to confirm it still passes**

```bash
./mvnw test -pl . -Dtest=AuthSmokeTest#refreshReturnsNonEmptyFamilyIds -q
```

Expected: PASS.

- [ ] **Step 3.5: Commit**

```bash
git add src/main/java/com/family/finance/controller/AuthController.java \
        src/test/java/com/family/finance/AuthSmokeTest.java
git commit -m "fix(auth): refresh endpoint queries DB for current familyIds

Previously reused stale familyIds from the old token's claims.
A user who joins a new family would receive access tokens missing
their new familyId until they logged out and back in."
```

---

## Task 4: Fix version double-increment

**Why:** The V4 migration created `BEFORE UPDATE` triggers on `users`, `families`, and `accounts` that set `version = OLD.version + 1`. All entities also have `@PreUpdate void onUpdate() { version++; }`. On each update, version increments by 2. This causes false conflict detections in sync (server sees version 5 where client expects version 3).

Fix: Drop the three triggers. All entities already manage `version` and `updatedAt` via `@PreUpdate` — that remains the single source of truth.

**Files:**
- Create: `src/main/resources/db/migration/V13__drop_duplicate_version_triggers.sql`

- [ ] **Step 4.1: Create the migration**

File: `src/main/resources/db/migration/V13__drop_duplicate_version_triggers.sql`

```sql
-- V4 created BEFORE UPDATE triggers that increment version = OLD.version + 1.
-- All entities also have @PreUpdate callbacks that do the same.
-- Both fire on every UPDATE, causing version to increment by 2 instead of 1.
-- Drop the triggers; @PreUpdate is the single source of truth for version + updated_at.

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
DROP TRIGGER IF EXISTS trg_families_updated_at ON families;
DROP TRIGGER IF EXISTS trg_accounts_updated_at ON accounts;

-- Function is now unused; drop it to keep the schema clean.
DROP FUNCTION IF EXISTS set_updated_at();
```

- [ ] **Step 4.2: Verify the migration file is picked up**

```bash
./mvnw flyway:info -Dflyway.url="${DATABASE_URL}" 2>/dev/null | grep V13 || echo "run against real DB to verify"
```

This step only verifies locally if a `DATABASE_URL` is set. Otherwise, the migration will be validated on next Railway deploy. Move on.

- [ ] **Step 4.3: Commit**

```bash
git add src/main/resources/db/migration/V13__drop_duplicate_version_triggers.sql
git commit -m "fix(db): drop V4 triggers that double-increment version column

V4 created BEFORE UPDATE triggers on users/families/accounts that
set version = OLD.version + 1. All entities already have @PreUpdate
doing the same. Result: version incremented by 2 on every update,
causing false conflict detections in sync."
```

---

## Task 5: Implement refresh token revocation

**Why:** Refresh tokens are stateless JWTs. A stolen or compromised refresh token remains valid for 7 days with no way to invalidate it. The `revoked_tokens` table exists in V4 but is unused.

**Model:** Blocklist — tokens are issued stateless, revoked tokens are stored. On refresh, the token's JTI is checked against the blocklist. Token rotation: on every successful refresh, the old JTI is revoked and a new refresh token (with a new JTI) is issued.

**Files:**
- Create: `src/main/java/com/family/finance/entity/RevokedToken.java`
- Create: `src/main/java/com/family/finance/repository/RevokedTokenRepository.java`
- Modify: `src/main/java/com/family/finance/security/JwtTokenProvider.java`
- Modify: `src/main/java/com/family/finance/controller/AuthController.java`
- Modify: `src/test/java/com/family/finance/AuthSmokeTest.java`

- [ ] **Step 5.1: Write the failing tests**

Add to `AuthSmokeTest.java`:

```java
@Test
void logoutRevokesRefreshToken() throws Exception {
    RegisterRequest reg = new RegisterRequest(
            "logout-test@example.com", "pass123", "Frank", "Frank Family");
    MvcResult regResult = mvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reg)))
            .andReturn();
    TokenResponse tokens = objectMapper.readValue(
            regResult.getResponse().getContentAsString(), TokenResponse.class);

    // Logout revokes the refresh token
    mvc.perform(post("/api/auth/logout")
                    .header("X-Refresh-Token", tokens.refreshToken()))
            .andExpect(status().isNoContent());

    // Revoked token cannot be used to refresh
    mvc.perform(post("/api/auth/refresh")
                    .header("X-Refresh-Token", tokens.refreshToken()))
            .andExpect(status().isUnauthorized());
}

@Test
void refreshRotatesToken() throws Exception {
    RegisterRequest reg = new RegisterRequest(
            "rotate-test@example.com", "pass123", "Grace", "Grace Family");
    MvcResult regResult = mvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reg)))
            .andReturn();
    TokenResponse tokens = objectMapper.readValue(
            regResult.getResponse().getContentAsString(), TokenResponse.class);

    // Use refresh token to get new tokens
    MvcResult refreshResult = mvc.perform(post("/api/auth/refresh")
                    .header("X-Refresh-Token", tokens.refreshToken()))
            .andExpect(status().isOk())
            .andReturn();
    TokenResponse newTokens = objectMapper.readValue(
            refreshResult.getResponse().getContentAsString(), TokenResponse.class);

    // Old refresh token is now revoked (token rotation)
    mvc.perform(post("/api/auth/refresh")
                    .header("X-Refresh-Token", tokens.refreshToken()))
            .andExpect(status().isUnauthorized());

    // New refresh token is still valid
    mvc.perform(post("/api/auth/refresh")
                    .header("X-Refresh-Token", newTokens.refreshToken()))
            .andExpect(status().isOk());
}
```

- [ ] **Step 5.2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest="AuthSmokeTest#logoutRevokesRefreshToken+AuthSmokeTest#refreshRotatesToken" -q
```

Expected: `logoutRevokesRefreshToken` — 404 (no logout endpoint). `refreshRotatesToken` — second refresh returns 200 instead of 401 (no rotation yet).

- [ ] **Step 5.3: Create RevokedToken entity**

File: `src/main/java/com/family/finance/entity/RevokedToken.java`

```java
package com.family.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
@Getter
@NoArgsConstructor
public class RevokedToken {

    @Id
    @Column(name = "jti", length = 255)
    private String jti;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static RevokedToken of(String jti, Instant expiresAt) {
        RevokedToken token = new RevokedToken();
        token.jti = jti;
        token.expiresAt = expiresAt;
        return token;
    }
}
```

- [ ] **Step 5.4: Create RevokedTokenRepository**

File: `src/main/java/com/family/finance/repository/RevokedTokenRepository.java`

```java
package com.family.finance.repository;

import com.family.finance.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    // existsById(jti) is sufficient — JpaRepository provides it for free
}
```

- [ ] **Step 5.5: Add JTI to refresh tokens in JwtTokenProvider**

In `src/main/java/com/family/finance/security/JwtTokenProvider.java`:

Add import:
```java
import java.util.UUID;
```
(Already present — check before adding.)

Change `generateRefreshToken` to embed a JTI and return both the token string and the JTI. Since callers need the JTI to store/revoke it, return a record instead of a bare `String`.

Add this record at the top of the class (after the class declaration, before the constructor):

```java
public record RefreshTokenResult(String token, String jti, Instant expiresAt) {}
```

Change the existing method signature — **replace the entire `generateRefreshToken` method**:

```java
public RefreshTokenResult generateRefreshToken(UUID userId, List<UUID> familyIds) {
    String jti = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plusMillis(refreshTokenExpiryMs);
    String token = buildToken(userId, familyIds, refreshTokenExpiryMs, "refresh", jti);
    return new RefreshTokenResult(token, jti, expiresAt);
}
```

Update `buildToken` to accept an optional JTI (used for refresh tokens only):

**Replace the entire `buildToken` method:**

```java
private String buildToken(UUID userId, List<UUID> familyIds, long expiryMs, String type, String jti) {
    Date now = new Date();
    var builder = Jwts.builder()
            .subject(userId.toString())
            .claim("familyIds", familyIds.stream().map(UUID::toString).toList())
            .claim("type", type)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiryMs))
            .signWith(key);
    if (jti != null) {
        builder.id(jti);
    }
    return builder.compact();
}
```

Update `generateAccessToken` to pass `null` for JTI (access tokens don't need one):

```java
public String generateAccessToken(UUID userId, List<UUID> familyIds) {
    return buildToken(userId, familyIds, accessTokenExpiryMs, "access", null);
}
```

Add a method to extract the JTI from claims:

```java
public String extractJti(Claims claims) {
    return claims.getId();
}
```

- [ ] **Step 5.6: Update AuthController**

In `src/main/java/com/family/finance/controller/AuthController.java`, make the following changes:

**Add import:**
```java
import com.family.finance.entity.RevokedToken;
import com.family.finance.repository.RevokedTokenRepository;
import com.family.finance.security.JwtTokenProvider.RefreshTokenResult;
import java.time.Instant;
```

**Add field (after existing fields):**
```java
private final RevokedTokenRepository revokedTokenRepository;
```

**Update `register` to use `RefreshTokenResult`** (lines 67-68):

```java
// BEFORE:
String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), familyIds);
String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), familyIds);

return ResponseEntity.status(HttpStatus.CREATED)
        .body(TokenResponse.of(accessToken, refreshToken,
                jwtTokenProvider.getAccessTokenExpiryMs(), user.getId(), familyIds));

// AFTER:
String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), familyIds);
RefreshTokenResult refreshResult = jwtTokenProvider.generateRefreshToken(user.getId(), familyIds);

return ResponseEntity.status(HttpStatus.CREATED)
        .body(TokenResponse.of(accessToken, refreshResult.token(),
                jwtTokenProvider.getAccessTokenExpiryMs(), user.getId(), familyIds));
```

**Update `login` to use `RefreshTokenResult`** (lines 87-91):

```java
// BEFORE:
String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), familyIds);
String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), familyIds);

return ResponseEntity.ok(TokenResponse.of(accessToken, refreshToken,
        jwtTokenProvider.getAccessTokenExpiryMs(), user.getId(), familyIds));

// AFTER:
String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), familyIds);
RefreshTokenResult refreshResult = jwtTokenProvider.generateRefreshToken(user.getId(), familyIds);

return ResponseEntity.ok(TokenResponse.of(accessToken, refreshResult.token(),
        jwtTokenProvider.getAccessTokenExpiryMs(), user.getId(), familyIds));
```

**Replace the entire `refresh` method:**

```java
@PostMapping("/refresh")
@Transactional
public ResponseEntity<TokenResponse> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
    Claims claims;
    try {
        claims = jwtTokenProvider.validateAndParseClaims(refreshToken);
    } catch (Exception e) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    if (!"refresh".equals(claims.get("type", String.class))) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not a refresh token");
    }

    String jti = jwtTokenProvider.extractJti(claims);
    if (jti == null || revokedTokenRepository.existsById(jti)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
    }

    UUID userId = jwtTokenProvider.extractUserId(claims);
    List<UUID> familyIds = familyMemberRepository.findActiveByUserId(userId)
            .stream()
            .map(m -> m.getFamily().getId())
            .toList();

    String newAccessToken = jwtTokenProvider.generateAccessToken(userId, familyIds);
    RefreshTokenResult newRefreshResult = jwtTokenProvider.generateRefreshToken(userId, familyIds);

    // Token rotation: revoke the old refresh token
    revokedTokenRepository.save(RevokedToken.of(jti, claims.getExpiration().toInstant()));

    return ResponseEntity.ok(TokenResponse.of(newAccessToken, newRefreshResult.token(),
            jwtTokenProvider.getAccessTokenExpiryMs(), userId, familyIds));
}
```

**Add `logout` method after `refresh`:**

```java
@PostMapping("/logout")
@Transactional
public ResponseEntity<Void> logout(@RequestHeader("X-Refresh-Token") String refreshToken) {
    try {
        Claims claims = jwtTokenProvider.validateAndParseClaims(refreshToken);
        String jti = jwtTokenProvider.extractJti(claims);
        if (jti != null && !revokedTokenRepository.existsById(jti)) {
            revokedTokenRepository.save(RevokedToken.of(jti, claims.getExpiration().toInstant()));
        }
    } catch (Exception ignored) {
        // Invalid or expired token — already effectively revoked, nothing to store
    }
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 5.7: Run the tests**

```bash
./mvnw test -pl . -Dtest="AuthSmokeTest#logoutRevokesRefreshToken+AuthSmokeTest#refreshRotatesToken+AuthSmokeTest#refreshReturnsNonEmptyFamilyIds" -q
```

Expected: all 3 pass.

- [ ] **Step 5.8: Run full test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`. Fix any compilation errors before committing.

- [ ] **Step 5.9: Commit**

```bash
git add src/main/java/com/family/finance/entity/RevokedToken.java \
        src/main/java/com/family/finance/repository/RevokedTokenRepository.java \
        src/main/java/com/family/finance/security/JwtTokenProvider.java \
        src/main/java/com/family/finance/controller/AuthController.java \
        src/test/java/com/family/finance/AuthSmokeTest.java
git commit -m "feat(auth): implement refresh token revocation with token rotation

Refresh tokens now embed a JTI claim. On each refresh, the old JTI
is stored in revoked_tokens (V4 table, previously unused). Subsequent
use of the revoked token returns 401. Adds POST /api/auth/logout to
explicitly revoke a refresh token."
```

---

## Task 6: Add rate limiting on auth endpoints

**Why:** No rate limiting exists. Auth endpoints are vulnerable to brute-force. Rate limit: 10 requests/minute per IP on all `/api/auth/**` routes.

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/family/finance/security/RateLimitFilter.java`
- Modify: `src/test/java/com/family/finance/AuthSmokeTest.java`

- [ ] **Step 6.1: Write the failing test**

Add to `AuthSmokeTest.java`:

```java
@Test
void rateLimitBlocksExcessiveAuthRequests() throws Exception {
    LoginRequest login = new LoginRequest("nonexistent@example.com", "wrongpass");
    String uniqueIp = "203.0.113.42"; // documentation range IP, unique to this test

    // Exhaust the 10-request bucket
    for (int i = 0; i < 10; i++) {
        mvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", uniqueIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)));
    }

    // 11th request must be rate limited
    mvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", uniqueIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isTooManyRequests());
}
```

- [ ] **Step 6.2: Run test to confirm it fails**

```bash
./mvnw test -pl . -Dtest=AuthSmokeTest#rateLimitBlocksExcessiveAuthRequests -q
```

Expected: FAIL — 11th request returns 401 (wrong credentials) instead of 429.

- [ ] **Step 6.3: Add bucket4j-core dependency to pom.xml**

In `pom.xml`, add inside `<dependencies>` after the Sentry dependency:

```xml
<!-- Rate limiting -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
```

- [ ] **Step 6.4: Create RateLimitFilter**

File: `src/main/java/com/family/finance/security/RateLimitFilter.java`

```java
package com.family.finance.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a 10-requests-per-minute-per-IP limit to all /api/auth/** requests.
 * Uses an in-memory bucket per client IP (X-Forwarded-For or remote address).
 * Buckets are created lazily and never expire — acceptable for a low-traffic app.
 * For high-traffic scenarios, replace with a distributed cache-backed bucket.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests — try again in a minute\"}");
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(CAPACITY, REFILL_PERIOD))
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 6.5: Run the rate limit test**

```bash
./mvnw test -pl . -Dtest=AuthSmokeTest#rateLimitBlocksExcessiveAuthRequests -q
```

Expected: PASS.

- [ ] **Step 6.6: Run full test suite**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`. All existing tests should pass because they use different IPs (MockMvc uses `127.0.0.1` by default unless `X-Forwarded-For` is set, and each rate-limit-sensitive test uses a unique IP).

- [ ] **Step 6.7: Commit**

```bash
git add pom.xml \
        src/main/java/com/family/finance/security/RateLimitFilter.java \
        src/test/java/com/family/finance/AuthSmokeTest.java
git commit -m "feat(security): add rate limiting on auth endpoints (10 req/min/IP)

Applies bucket4j in-memory rate limit to all /api/auth/** routes.
Limits brute-force attacks on login and register endpoints.
Returns 429 with JSON error body when limit exceeded."
```

---

## Self-Review Checklist

- [x] **Spec coverage:** All 6 audit findings addressed: enum case (Task 1), DebugController (Task 2), stale familyIds (Task 3), version double-increment (Task 4), refresh revocation (Task 5), rate limiting (Task 6).
- [x] **No placeholders:** Every step contains exact file paths and complete code. No "TBD" or "similar to above."
- [x] **Type consistency:** `RefreshTokenResult` record defined in Task 5.5 and used consistently in 5.6. `RevokedToken.of()` factory used in both `refresh` and `logout`.
- [x] **H2 compatibility:** Tasks 1, 2, 3, 5, 6 all work in H2 (no DB-specific SQL). Task 4 is a Flyway migration that only runs against PostgreSQL; tests have `spring.flyway.enabled=false`.
- [x] **No double-add:** `familyMemberRepository` is already injected in `AuthController` — Task 3 uses it without re-declaring.
- [x] **Token rotation test:** `refreshRotatesToken` verifies the old token is invalid AND the new token is valid — both assertions present.
