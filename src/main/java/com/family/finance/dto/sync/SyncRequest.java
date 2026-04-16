package com.family.finance.dto.sync;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * WatermelonDB synchronize() push payload.
 *
 * Client sends this on every sync. The server processes pushed changes
 * first, then returns pulled changes in SyncResponse.
 */
public record SyncRequest(
        @JsonProperty("lastPulledAt") Long lastPulledAt,
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("changes") Map<String, SyncTableChanges> changes
) {}
