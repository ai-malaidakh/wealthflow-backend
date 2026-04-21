# Sync Protocol Details

> Read this when working on `SyncController`, `SyncService`, any `SyncTableHandler`, or debugging a sync round-trip.

The mobile `syncEngine.ts` calls **push first, then pull** (WatermelonDB convention).

## Wire Format

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

**Money fields:** Backend stores `NUMERIC(12,2)`, serializes as decimal string (e.g. `"balance": "1234.56"`). WatermelonDB stores integer cents. **The multiply/divide-by-100 conversion lives on the mobile side only** — the backend does not convert.

**Timestamp fields:** Serialized as epoch milliseconds (long integers).

## Conflict Detection (Server Wins)

When a push arrives where `clientVersion < serverVersion`:
1. Both snapshots written to `sync_conflicts` (JSONB).
2. Client changes **not applied** — server version stands.
3. Record returned in next pull with server-version data.
4. Mobile shows a conflict badge. Resolution UI is web-only (Phase 8).

## Adding a New Syncable Table

1. Implement `SyncTableHandler` — `tableName()` must match the WatermelonDB model's table name exactly.
2. Annotate `@Component` — `SyncService` auto-discovers all handler beans.
3. Implement `applyPush()` (version check + delete latch + conflict log) and `buildPull()`.
4. No changes needed to `SyncController` or `SyncService`.
5. Coordinate the matching WatermelonDB schema change with the frontend agent — see `wealthflow-master/CLAUDE.md §6` for the full cross-repo checklist.
