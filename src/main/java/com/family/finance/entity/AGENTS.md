<!-- Parent: ../../../../../../../AGENTS.md -->
<!-- Generated: 2026-04-22 | Updated: 2026-04-22 -->

# entity/

## Purpose
JPA entities mapping to PostgreSQL tables. Each entity follows the audit pattern: `version` (long), `updatedAt` (Instant), `deletedAt` (Instant, nullable), and a `@PreUpdate` hook that increments `version` and sets `updatedAt`.

## Key Files

| File | Table | Notes |
|------|-------|-------|
| `User.java` | `users` | Password hash (BCrypt), email unique |
| `Family.java` | `families` | Name only; members via `FamilyMember` |
| `FamilyMember.java` | `family_members` | Role: `admin` \| `member` |
| `FamilyInvite.java` | `family_invites` | Invite code + expiry — server-side only, not in mobile schema |
| `Account.java` | `accounts` | `balance NUMERIC(12,2)` — always `BigDecimal` |
| `Category.java` | `categories` | `type`: `INCOME` \| `EXPENSE` |
| `Transaction.java` | `transactions` | `amount NUMERIC(12,2)`, `importHash VARCHAR(64)` (V13) |
| `Budget.java` | `budgets` | `amount NUMERIC(12,2)`, `periodStart` / `periodEnd` dates |
| `SyncConflict.java` | `sync_conflicts` | Server-wins conflict log — server-side only |

## For AI Agents

### Working In This Directory

**Every mutable entity must follow the audit pattern** (required for sync):
```java
@Column(nullable = false)
private long version = 1L;

@Column(nullable = false)
private Instant updatedAt;

@Column
private Instant deletedAt;   // null = active, non-null = soft deleted

@PreUpdate
protected void onUpdate() {
    this.version++;
    this.updatedAt = Instant.now();
}
```

**Money is always `NUMERIC(12,2)` / `BigDecimal`.** Never use `double`, `float`, or `DECIMAL` without explicit precision. The corresponding Flyway migration must specify `NUMERIC(12,2)`.

**Soft deletes are a one-way latch** (CLAUDE.md §4.6). Once `deletedAt` is set, no update may clear it. The repository `@Query` must filter `WHERE deleted_at IS NULL` on all reads.

**`FamilyInvite` and `SyncConflict` are server-side only** — not in the WatermelonDB mobile schema. Do not add them to `src/database/schema.ts` in the frontend.

**Schema is Flyway-owned.** After changing an entity, write a new migration `V{next}__describe_change.sql`. Current applied: V1–V13; next is `V14`.

### Adding a New Entity
1. Read `docs/agents/authoring-rules.md`
2. Create entity with full audit pattern
3. Create Flyway migration `V14__...`
4. Create repository with tenant-scoped queries
5. Create `SyncTableHandler` if the entity is syncable
6. Update `packages/types/index.ts` if the entity has an API response shape

### Testing Requirements
- `@PreUpdate` fires: save entity, update it, assert `version` incremented and `updatedAt` changed
- Soft delete: deleted record does not appear in repository reads

## Dependencies

### External
- Spring Data JPA / Hibernate — ORM
- Lombok — `@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j`
- PostgreSQL — `NUMERIC(12,2)` for all money columns

<!-- MANUAL: -->
