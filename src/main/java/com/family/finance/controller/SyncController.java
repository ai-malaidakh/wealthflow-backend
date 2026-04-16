package com.family.finance.controller;

import com.family.finance.dto.sync.SyncRequest;
import com.family.finance.dto.sync.SyncResponse;
import com.family.finance.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Phase 3: WatermelonDB push/pull sync endpoints.
 *
 * WatermelonDB's synchronize() calls pullChanges and pushChanges separately.
 * These map to:
 *
 *   GET  /api/sync/pull?lastPulledAt=<epoch_ms>&schemaVersion=<int>
 *        → { changes: { <table>: { created, updated, deleted } }, timestamp: <epoch_ms> }
 *
 *   POST /api/sync/push?lastPulledAt=<epoch_ms>
 *        body: { changes: { <table>: { created, updated, deleted } } }
 *        → { timestamp: <epoch_ms> }
 *
 * Auth: JWT required on both endpoints. TenantContext is populated by JwtAuthenticationFilter.
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    /**
     * Pull endpoint: return all server-side changes since lastPulledAt.
     *
     * @param lastPulledAt epoch milliseconds from the client's last successful sync
     *                     (omit or null for a full sync)
     * @param schemaVersion client schema version — reserved for future migration handling
     */
    @GetMapping("/pull")
    public ResponseEntity<SyncResponse> pull(
            @RequestParam(required = false) Long lastPulledAt,
            @RequestParam(defaultValue = "1") int schemaVersion) {

        SyncResponse response = syncService.pull(lastPulledAt);
        return ResponseEntity.ok(response);
    }

    /**
     * Push endpoint: apply client-side changes to the server.
     *
     * @param lastPulledAt epoch ms from the client's last pull — used to determine
     *                     server-side context for conflict detection
     * @param request pushed changes per table
     */
    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push(
            @RequestParam(required = false) Long lastPulledAt,
            @RequestBody SyncRequest request) {

        long serverTimestamp = syncService.push(request);
        return ResponseEntity.ok(Map.of("timestamp", serverTimestamp));
    }
}
