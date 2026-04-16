package com.family.finance.service;

import com.family.finance.dto.sync.SyncRequest;
import com.family.finance.dto.sync.SyncResponse;
import com.family.finance.dto.sync.SyncTableChanges;
import com.family.finance.security.TenantContext;
import com.family.finance.sync.SyncTableHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 3: WatermelonDB push/pull sync service.
 *
 * Protocol:
 *   1. Client calls POST /api/sync/push with changed records since lastPulledAt.
 *      Server applies push (server version wins on conflict; conflicts logged to sync_conflicts).
 *   2. Client calls GET /api/sync/pull?lastPulledAt=... to fetch server-side changes.
 *      Client stores the returned timestamp as the new lastPulledAt.
 *
 * Server timestamp is captured BEFORE processing the push — so records written
 * during this push are included in the very next pull.
 *
 * Table support is pluggable: register a SyncTableHandler bean per table.
 * Handlers are auto-discovered via Spring's constructor injection.
 */
@Slf4j
@Service
public class SyncService {

    private final Map<String, SyncTableHandler> handlers;

    public SyncService(List<SyncTableHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(SyncTableHandler::tableName, Function.identity()));
        log.info("SyncService registered table handlers: {}", handlers.keySet());
    }

    /**
     * Apply client-pushed changes to the server.
     *
     * @param request pushed changes per table
     * @return serverTimestamp (epoch ms) to return to the client as confirmation
     */
    @Transactional
    public long push(SyncRequest request) {
        UUID userId = TenantContext.getCurrentUserId();
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();

        // Capture server timestamp BEFORE writes — records modified here are
        // included in the next pull that presents this timestamp as lastPulledAt.
        long serverTimestamp = Instant.now().toEpochMilli();

        Map<String, SyncTableChanges> changes = request.changes();
        if (changes != null) {
            for (Map.Entry<String, SyncTableChanges> entry : changes.entrySet()) {
                String table = entry.getKey();
                SyncTableHandler handler = handlers.get(table);
                if (handler == null) {
                    log.warn("Sync push: no handler for table '{}', skipping", table);
                    continue;
                }
                SyncTableChanges tableChanges = entry.getValue();
                log.debug("Sync push: table={} created={} updated={} deleted={}",
                        table, tableChanges.created().size(),
                        tableChanges.updated().size(), tableChanges.deleted().size());
                handler.applyPush(tableChanges, userId, familyIds);
            }
        }

        return serverTimestamp;
    }

    /**
     * Return all records changed server-side since lastPulledAt.
     *
     * @param lastPulledAt client's last known timestamp (null triggers a full sync)
     * @return pull payload + new server timestamp
     */
    @Transactional
    public SyncResponse pull(Long lastPulledAt) {
        UUID userId = TenantContext.getCurrentUserId();
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();

        Instant since = lastPulledAt != null
                ? Instant.ofEpochMilli(lastPulledAt)
                : Instant.EPOCH;
        Instant until = Instant.now();

        Map<String, SyncTableChanges> responseChanges = new HashMap<>();
        for (Map.Entry<String, SyncTableHandler> entry : handlers.entrySet()) {
            responseChanges.put(entry.getKey(),
                    entry.getValue().buildPull(since, until, userId, familyIds));
        }

        return new SyncResponse(responseChanges, until.toEpochMilli());
    }
}
