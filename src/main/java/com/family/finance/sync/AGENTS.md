<!-- Parent: ../../../../../../../AGENTS.md -->
<!-- Generated: 2026-04-22 | Updated: 2026-04-22 -->

# sync/

## Purpose
WatermelonDB sync engine — server side. `SyncService` orchestrates pull and push for all tables. Each table has a dedicated `SyncTableHandler` implementation that is auto-discovered via `@Component`. `SyncConflictLogger` records server-wins conflicts.

## Key Files

| File | Description |
|------|-------------|
| `SyncTableHandler.java` | Interface — all table handlers implement this |
| `AccountSyncHandler.java` | Pull/push for `accounts` table |
| `CategorySyncHandler.java` | Pull/push for `categories` table |
| `TransactionSyncHandler.java` | Pull/push for `transactions` table |
| `BudgetSyncHandler.java` | Pull/push for `budgets` table |
| `SyncConflictLogger.java` | Records server-wins conflicts to `sync_conflicts` table |

## For AI Agents

### Working In This Directory

**Auto-discovery:** `SyncService` collects all `@Component` beans that implement `SyncTableHandler`. Adding a new handler requires only implementing the interface and annotating with `@Component` — no registration step.

**SyncTableHandler interface contract:**
```java
String getTableName();           // must match WatermelonDB table name exactly (snake_case)
SyncTableChanges pull(Instant since, List<UUID> familyIds, UUID userId);
void push(SyncTableChanges changes, UUID userId, List<UUID> familyIds);
```

**Money fields:** All money is `NUMERIC(12,2)` / `BigDecimal` in the DB. The sync handler serialises to/from `String` decimal format (`"19.99"`). The mobile side converts to/from integer cents. Never use `double` or `float`.

**Tenant scoping:** Every pull query must filter by `familyIds` and/or `userId` from `TenantContext`. Never return records outside the authenticated tenant's scope.

**Conflict resolution (server wins):**
- Compare `clientVersion` (from push payload) with `serverVersion` (current DB value)
- If `clientVersion < serverVersion` → reject client change, log via `SyncConflictLogger`, return current server record on next pull
- If versions match → apply client change, increment `version`, set `updatedAt`

**Soft deletes in push:** Once `deletedAt` is set server-side, a sync push cannot clear it. The `push()` implementation must ignore `deletedAt: null` from the client if the server record already has `deletedAt` set.

### Adding a New Syncable Entity

See CLAUDE.md §6 "Adding a new syncable entity" for the full cross-repo checklist. Backend steps:
1. New Flyway migration: `V{next}__create_{table}.sql`
2. New JPA entity with `version`, `updatedAt`, `deletedAt`, `@PreUpdate` hook
3. New repository with tenant-scoped queries
4. New `SyncTableHandler` implementation annotated `@Component` — auto-discovered
5. Verify `getTableName()` matches WatermelonDB table name in `src/database/schema.ts`

### Testing Requirements
- `SyncRoundTripTest.java` covers the happy path — extend it for new tables
- Assert tenant isolation: user A cannot pull user B's records
- Assert soft-delete latch: a push with `deletedAt: null` cannot resurrect a deleted record

## Dependencies

### Internal
- `entity/` — JPA entities with `@PreUpdate` audit hooks
- `repository/` — tenant-scoped Spring Data repositories
- `security/TenantContext.java` — provides `getCurrentFamilyIds()` and `getCurrentUserId()`
- `dto/sync/` — `SyncRequest`, `SyncResponse`, `SyncTableChanges` DTOs

### External
- Spring Data JPA — repository queries
- `SyncController` — calls `SyncService.pull()` and `SyncService.push()`

<!-- MANUAL: -->
