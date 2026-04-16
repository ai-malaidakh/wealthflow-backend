-- sync_conflicts: logs every conflict detected during a push.
-- A conflict occurs when the client pushes a record whose version
-- is lower than the server's current version (last-write-wins would
-- silently overwrite data; instead we log and server-version wins).
--
-- Records here are append-only. Resolution metadata is written when
-- the conflict is surfaced and acknowledged by the client.

CREATE TABLE sync_conflicts (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Which table and record the conflict is on
    table_name      VARCHAR(50)  NOT NULL,
    record_id       UUID         NOT NULL,

    -- User whose sync push triggered the conflict
    user_id         UUID         NOT NULL REFERENCES users(id),

    -- Version numbers at conflict time
    client_version  BIGINT       NOT NULL,
    server_version  BIGINT       NOT NULL,

    -- Full snapshots of both sides at conflict time (JSONB for queryability)
    client_data     JSONB        NOT NULL,
    server_data     JSONB        NOT NULL,

    -- Lifecycle
    detected_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ,
    resolution      VARCHAR(20)  CHECK (resolution IN ('server_wins', 'client_wins', 'merged'))
);

CREATE INDEX idx_sync_conflicts_record  ON sync_conflicts(table_name, record_id);
CREATE INDEX idx_sync_conflicts_user    ON sync_conflicts(user_id);
CREATE INDEX idx_sync_conflicts_pending ON sync_conflicts(user_id) WHERE resolved_at IS NULL;
