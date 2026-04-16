package com.family.finance.dto.sync;

import java.util.List;
import java.util.Map;

/**
 * Represents the pushed or pulled changes for a single table.
 * Follows WatermelonDB's synchronize() protocol exactly.
 */
public record SyncTableChanges(
        List<Map<String, Object>> created,
        List<Map<String, Object>> updated,
        List<String> deleted
) {
    public static SyncTableChanges empty() {
        return new SyncTableChanges(List.of(), List.of(), List.of());
    }
}
