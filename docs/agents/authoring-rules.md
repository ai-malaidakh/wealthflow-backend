# Authoring Rules — Entities, Tenancy & New Endpoints

> Read this before writing any new entity, repository query, or controller endpoint.

## Tenant Isolation (Never Skip)

Cross-family data leakage is a critical security failure.

1. `JwtAuthenticationFilter` populates `TenantContext` (userId + familyIds) from every JWT. `TenantContextInterceptor` clears it after the request completes.
2. All repository queries **must** filter by `TenantContext.getCurrentFamilyIds()` and/or `TenantContext.getCurrentUserId()`. Follow the `findByIdAndTenant(id, familyIds, userId)` pattern in `AccountRepository`, `CategoryRepository`, `TransactionRepository`.
3. Family membership check before any family-scoped write: `TenantContext.getCurrentFamilyIds().contains(request.familyId())`. Throw 403 if not a member.
4. Admin-only operations: use `FamilyService.requireAdminMembership(familyId)` — throws 403 if caller is not ADMIN.
5. Integration test requirement: for every new entity, prove user A cannot read, update, or delete user B's records.

## Entity Pattern (Required)

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
    version++;   // REQUIRED — sync conflict detection reads this
}
```

Additional rules:
- Never use `ddl-auto: create` or `update` — all schema changes go through a new Flyway migration.
- Set IDs via `entity.setId(UUID.randomUUID())` on creation, not auto-generated sequences. This allows client-generated IDs from sync push.
- Money fields: `BigDecimal`, `@Column(precision = 12, scale = 2)`. Convert from wire via `new BigDecimal(value.toString())` — never cast to float.

## New Endpoint Checklist

For every new controller method:

- [ ] Use `TenantContext.getCurrentUserId()` and `TenantContext.getCurrentFamilyIds()` — never trust user-supplied IDs for ownership
- [ ] Soft delete (set `deleted_at`), not hard delete — except the GDPR purge endpoint
- [ ] Validate input with `@Valid` + Jakarta constraint annotations on the request record
- [ ] Throw `ResponseStatusException` with the correct HTTP status — do not let JPA exceptions propagate
- [ ] Return `ResponseEntity<T>` with explicit status codes (201 for creates, 204 for deletes)
- [ ] New entity follows the `@PreUpdate` / `version` / `deletedAt` pattern above
- [ ] Write an integration test asserting cross-tenant access is denied (403 or 404)
- [ ] New Flyway migration is `V{n+1}__description.sql` — never edit an applied migration
