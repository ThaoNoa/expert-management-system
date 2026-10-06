package com.npcore.ems.expert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "expert_certificates")
@Getter
@Setter
public class ExpertCertificate implements ExpertChild {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "certificate_id")
    private UUID id;
    @Column(name = "expert_id", nullable = false, updatable = false)
    private UUID expertId;
    @Column(name = "certificate_name", nullable = false)
    private String certificateName;
    @Column(name = "certificate_no")
    private String certificateNo;
    private String issuer;
    @Column(name = "standard_id")
    private UUID standardId;
    @Column(name = "issued_date")
    private LocalDate issuedDate;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    @Column(name = "document_id")
    private UUID documentId;
    /** VALID | EXPIRED | REVOKED */
    @Column(nullable = false)
    private String status = "VALID";
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
}
