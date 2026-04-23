<!-- Parent: ../../../../../../../AGENTS.md -->
<!-- Generated: 2026-04-22 | Updated: 2026-04-22 -->

# security/

## Purpose
JWT authentication and tenant context. `JwtAuthenticationFilter` validates every request, populates `TenantContext`, and delegates to Spring Security. `TenantContext` is the single source of userId/familyIds throughout a request.

## Key Files

| File | Description |
|------|-------------|
| `SecurityConfig.java` | Spring Security config — stateless, public routes, filter chain |
| `JwtTokenProvider.java` | JWT creation, parsing, and validation (jjwt 0.12.5, HMAC-SHA256) |
| `JwtAuthenticationFilter.java` | Per-request filter: extracts Bearer token, validates, calls `TenantContext.setCurrentTenant()` |
| `TenantContext.java` | `ThreadLocal` storage for `userId` (UUID) and `familyIds` (List\<UUID\>) — must be cleared after each request |
| `UserDetailsServiceImpl.java` | Loads `UserDetails` from the database for Spring Security |

## For AI Agents

### Working In This Directory

**Public routes** (no auth required): `/api/auth/**`, `/api/health`, `/actuator/health`. Everything else requires `Authorization: Bearer <token>`.

**`TenantContextInterceptor` clears, it does not populate.** `JwtAuthenticationFilter` does the population in `doFilterInternal()`. `TenantContextInterceptor.afterCompletion()` calls `TenantContext.clear()` to prevent ThreadLocal leaks between requests.

**JWT claims:**
- `userId` — UUID string
- `familyIds` — JSON array of UUID strings

**Token lifetimes** (CLAUDE.md §4.3):
- Access token: 15 minutes
- Refresh token: 7 days, sent in `X-Refresh-Token` request header

**Adding a new public endpoint:** Add the path to `SecurityConfig` `permitAll()` list. Do not add broad wildcards — be specific.

**`DebugController` is `@Profile("dev")` only.** Never remove that annotation.

### Testing Requirements
- Unauthenticated request to protected endpoint → 401
- Expired access token → 401 (mobile interceptor handles refresh)
- Valid token with wrong `familyId` → repository query returns empty (tenant isolation, not 403)
- `TenantContext` is clear after request completes (test via `TenantContext.getCurrentUserId()` in a new request)

## Dependencies

### Internal
- `entity/User.java` — loaded by `UserDetailsServiceImpl`
- `repository/UserRepository.java`
- `config/TenantContextInterceptor.java` — clears `TenantContext` post-request

### External
- `io.jsonwebtoken:jjwt` 0.12.5 — JWT parsing and signing
- Spring Security — filter chain, `UsernamePasswordAuthenticationToken`

<!-- MANUAL: -->
