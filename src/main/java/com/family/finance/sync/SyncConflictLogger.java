package com.family.finance.sync;

import com.family.finance.entity.SyncConflict;
import com.family.finance.entity.User;
import com.family.finance.repository.SyncConflictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Logs conflicts detected during sync push processing.
 *
 * A conflict occurs when the client pushes a record whose version
 * is strictly less than the server's current version. The server
 * version always wins; this log allows clients to surface conflicts
 * to the user if needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncConflictLogger {

    private final SyncConflictRepository conflictRepository;

    /**
     * Record a conflict. The server snapshot is already in the DB at this point;
     * pass the server's current wire-format representation.
     */
    public void log(String tableName, UUID recordId, User user,
                    long clientVersion, long serverVersion,
                    Map<String, Object> clientData, Map<String, Object> serverData) {

        log.info("Sync conflict: table={} record={} clientVersion={} serverVersion={}",
                tableName, recordId, clientVersion, serverVersion);

        SyncConflict conflict = SyncConflict.of(
                tableName, recordId, user,
                clientVersion, serverVersion,
                clientData, serverData
        );
        conflictRepository.save(conflict);
    }
}
