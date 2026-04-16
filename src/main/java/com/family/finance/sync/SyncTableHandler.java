package com.family.finance.sync;

import com.family.finance.dto.sync.SyncTableChanges;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pluggable handler for one WatermelonDB table in the sync protocol.
 *
 * Phase 3 registers handlers for every syncable table. Adding a new
 * table to sync requires only a new SyncTableHandler bean — no changes
 * to SyncService or SyncController.
 */
public interface SyncTableHandler {

    /** WatermelonDB table name, e.g. "accounts". */
    String tableName();

    /**
     * Apply pushed changes from the client for this table.
     * Server-version wins on conflict; conflicts are logged via SyncConflictLogger.
     */
    void applyPush(SyncTableChanges changes, UUID userId, List<UUID> familyIds);

    /**
     * Build the pull payload for records modified in (since, until].
     * Must include soft-deleted records (client uses deletedAt to build the deleted list).
     */
    SyncTableChanges buildPull(Instant since, Instant until, UUID userId, List<UUID> familyIds);

    /**
     * Convert a persisted entity to the wire format expected by WatermelonDB.
     * Returned map keys must match the WatermelonDB column names exactly.
     */
    Map<String, Object> toWireFormat(Object entity);
}
