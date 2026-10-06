package com.npcore.ems.expert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "expert_educations")
@Getter
@Setter
public class ExpertEducation implements ExpertChild {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "education_id")
    private UUID id;
    @Column(name = "expert_id", nullable = false, updatable = false)
    private UUID expertId;
    @Column(name = "degree_level_code", nullable = false)
    private String degreeLevelCode;
    @Column(name = "field_id")
    private UUID fieldId;
    private String major;
    @Column(nullable = false)
    private String institution;
    @Column(name = "graduation_year")
    private Short graduationYear;
    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;
    @Column(nullable = false)
    private boolean verified;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
}
