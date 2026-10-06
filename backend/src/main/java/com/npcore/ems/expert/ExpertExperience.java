package com.npcore.ems.expert;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "expert_experiences")
@Getter
@Setter
public class ExpertExperience implements ExpertChild {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "experience_id")
    private UUID id;
    @Column(name = "expert_id", nullable = false, updatable = false)
    private UUID expertId;
    @Column(name = "industry_id")
    private UUID industryId;
    @Column(nullable = false)
    private String field;
    private String position;
    private String organization;
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;
    @Column(name = "to_date")
    private LocalDate toDate;
    @Column(name = "is_current", nullable = false)
    private boolean current;
    /** Mốc đã xác nhận cho kinh nghiệm đang diễn ra. */
    @Column(name = "verified_until")
    private LocalDate verifiedUntil;
    private String description;
    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expert_experience_codes", joinColumns = @JoinColumn(name = "experience_id"))
    @Column(name = "code_id")
    private Set<UUID> codeIds = new HashSet<>();
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    /**
     * BR-2.3.1..3 / BR-DATA-003: số năm KHÔNG tự tăng theo thời gian thực.
     * Tính đến to_date (đã kết thúc) hoặc verified_until (đang làm, đã xác nhận đến mốc đó).
     */
    public double years() {
        LocalDate end = toDate != null ? toDate : verifiedUntil;
        if (end == null || end.isBefore(fromDate)) return 0;
        double y = ChronoUnit.DAYS.between(fromDate, end) / 365.25;
        return Math.round(y * 10) / 10.0;
    }
}
