package com.npcore.ems.competency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Bằng chứng minh chứng cho năng lực chuyên gia.
 * Có thể liên kết tài liệu từ kho (document_id) hoặc liên kết trực tiếp tới
 * đối tượng trong hồ sơ (EDUCATION, EXPERIENCE, TRAINING, CERTIFICATE...) mà không phải upload lại.
 */
@Entity
@Table(name = "competency_evidence")
@Getter
@Setter
public class CompetencyEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "evidence_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expert_competency_id", nullable = false)
    private ExpertCompetency expertCompetency;

    @Column(name = "document_id")
    private UUID documentId;

    /** EDUCATION | EXPERIENCE | TRAINING | CERTIFICATE | AUDIT_LOG | COMPETENCE_TEST | WITNESS | INTERVIEW | OTHER */
    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Column(name = "source_object_type")
    private String sourceObjectType;

    @Column(name = "source_object_id")
    private UUID sourceObjectId;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
