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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "experts")
@Getter
@Setter
public class Expert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "expert_id")
    private UUID id;
    @Column(name = "expert_code", nullable = false, updatable = false)
    private String code;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    private String gender;
    @Column(name = "id_number")
    private String idNumber;
    private String address;
    private String phone;
    private String email;
    @Column(name = "home_location_id")
    private UUID homeLocationId;
    /** AUDITOR (CGĐG) | TECHNICAL_EXPERT (CGKT) | BOTH */
    @Column(name = "expert_type", nullable = false)
    private String expertType;
    /** FULLTIME | PARTTIME */
    @Column(name = "employment_type", nullable = false)
    private String employmentType;
    @Column(name = "department_id")
    private UUID departmentId;
    private String position;
    @Column(name = "joined_date")
    private LocalDate joinedDate;
    /** DRAFT | SUBMITTED | ACTIVE | SUSPENDED | INACTIVE - độc lập với trạng thái từng năng lực (BR-4.3.1). */
    @Column(nullable = false)
    private String status = "DRAFT";
    @Column(name = "status_reason")
    private String statusReason;
    /** Ngày cuối cùng bị dừng đánh giá (null = dừng tới khi GĐCN mở lại). */
    @Column(name = "suspended_until")
    private LocalDate suspendedUntil;
    @Column(name = "max_mandays_per_month", precision = 4, scale = 1)
    private BigDecimal maxMandaysPerMonth;
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
}
