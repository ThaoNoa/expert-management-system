package com.npcore.ems.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "document_versions")
@Getter
@Setter
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "version_id")
    private UUID id;
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private long fileSize;
    @Column(name = "storage_bucket", nullable = false)
    private String storageBucket;
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    @Column(nullable = false, columnDefinition = "bpchar(64)")
    private String sha256;
    @Column(name = "issued_date")
    private LocalDate issuedDate;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;
    /** PENDING_VERIFICATION | VERIFIED | REJECTED | SUPERSEDED | EXPIRED */
    @Column(nullable = false)
    private String status = "PENDING_VERIFICATION";
    @Column(name = "reject_reason")
    private String rejectReason;
}
