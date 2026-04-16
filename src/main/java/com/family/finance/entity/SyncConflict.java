package com.family.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "sync_conflicts")
@Getter
@Setter
@NoArgsConstructor
public class SyncConflict {

    public enum Resolution {
        SERVER_WINS, CLIENT_WINS, MERGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "table_name", nullable = false, length = 50)
    private String tableName;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "client_version", nullable = false)
    private Long clientVersion;

    @Column(name = "server_version", nullable = false)
    private Long serverVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> clientData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "server_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> serverData;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 20)
    private Resolution resolution;

    public static SyncConflict of(String tableName, UUID recordId, User user,
                                   long clientVersion, long serverVersion,
                                   Map<String, Object> clientData,
                                   Map<String, Object> serverData) {
        SyncConflict c = new SyncConflict();
        c.tableName = tableName;
        c.recordId = recordId;
        c.user = user;
        c.clientVersion = clientVersion;
        c.serverVersion = serverVersion;
        c.clientData = clientData;
        c.serverData = serverData;
        return c;
    }
}
