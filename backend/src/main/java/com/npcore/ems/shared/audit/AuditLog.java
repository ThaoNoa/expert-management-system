package com.npcore.ems.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng append-only: DB chặn UPDATE/DELETE và tự tính chuỗi hash (prev_hash, row_hash). */
@Entity
@Table(name = "audit_logs")
@Immutable
@Getter
@Setter
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "user_id")
    private UUID userId;

    private String username;

    @Column(nullable = false)
    private String action;

    @Column(name = "object_type", nullable = false)
    private String objectType;

    @Column(name = "object_id")
    private String objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "from_value", columnDefinition = "jsonb")
    private JsonNode fromValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_value", columnDefinition = "jsonb")
    private JsonNode toValue;

    private String reason;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "prev_hash", columnDefinition = "bpchar(64)", insertable = false, updatable = false)
    private String prevHash;

    /** Trigger DB ghi đè giá trị này. */
    @Column(name = "row_hash", columnDefinition = "bpchar(64)", nullable = false)
    private String rowHash = "pending";
}
