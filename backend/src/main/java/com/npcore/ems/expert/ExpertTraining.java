package com.npcore.ems.expert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "expert_trainings")
@Getter
@Setter
public class ExpertTraining implements ExpertChild {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "training_id")
    private UUID id;
    @Column(name = "expert_id", nullable = false, updatable = false)
    private UUID expertId;
    @Column(name = "training_name", nullable = false)
    private String trainingName;
    private String provider;
    @Column(name = "standard_id")
    private UUID standardId;
    @Column(name = "training_type")
    private String trainingType;
    @Column(name = "from_date")
    private LocalDate fromDate;
    @Column(name = "to_date")
    private LocalDate toDate;
    @Column(precision = 6, scale = 1)
    private BigDecimal hours;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(name = "certificate_id")
    private UUID certificateId;
    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
}
