package com.npcore.ems.competency;

import com.npcore.ems.expert.Expert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Hồ sơ năng lực của chuyên gia: liên kết giữa Expert và CompetencyDefinition.
 * Có quản lý revision, trạng thái duyệt (workflow), và thời hạn hiệu lực.
 */
@Entity
@Table(name = "expert_competencies")
@Getter
@Setter
public class ExpertCompetency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "expert_competency_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expert_id", nullable = false)
    private Expert expert;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competency_definition_id", nullable = false)
    private CompetencyDefinition competencyDefinition;

    @Column(name = "standard_version_id")
    private UUID standardVersionId;

    /** IN_TRAINING | QUALIFIED | SENIOR */
    @Column(name = "competency_level", nullable = false)
    private String competencyLevel = "QUALIFIED";

    /** DRAFT | SUBMITTED | UNDER_REVIEW | NEED_REVISION | APPROVED | REVIEW_REQUIRED | SUSPENDED | EXPIRED | REVOKED | SUPERSEDED | REJECTED */
    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "revision_no", nullable = false)
    private int revisionNo = 1;

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** Ngày phê duyệt lần đầu theo từng Code (BR-4.2.4 / BR-4.2.5). */
    @Column(name = "first_approved_date")
    private LocalDate firstApprovedDate;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
}
