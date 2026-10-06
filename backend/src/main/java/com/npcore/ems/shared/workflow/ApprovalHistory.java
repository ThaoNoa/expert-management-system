package com.npcore.ems.shared.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Lịch sử phê duyệt dùng chung (append-only). */
@Entity
@Table(name = "approval_history")
@Immutable
@Getter
@Setter
public class ApprovalHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;
    @Column(name = "object_type", nullable = false)
    private String objectType;
    @Column(name = "object_id", nullable = false)
    private UUID objectId;
    @Column(nullable = false)
    private String action;
    @Column(name = "from_status")
    private String fromStatus;
    @Column(name = "to_status", nullable = false)
    private String toStatus;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    private String decision;
    private String comment;
    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode snapshot;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
