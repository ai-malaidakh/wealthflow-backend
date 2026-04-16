package com.family.finance.dto.sync;

import java.util.Map;

/**
 * WatermelonDB synchronize() pull response.
 *
 * Client stores {@code timestamp} as {@code lastPulledAt} for the next sync.
 */
public record SyncResponse(
        Map<String, SyncTableChanges> changes,
        long timestamp
) {}
