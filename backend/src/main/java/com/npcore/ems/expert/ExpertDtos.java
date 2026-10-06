package com.npcore.ems.expert;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ExpertDtos {
    private ExpertDtos() {}

    public record ExpertRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Past LocalDate dateOfBirth,
            @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
            @Pattern(regexp = "\\d{9}|\\d{12}", message = "CMND 9 số hoặc CCCD 12 số") String idNumber,
            @Size(max = 500) String address,
            @Size(max = 30) String phone,
            @Email @Size(max = 255) String email,
            @NotNull @Pattern(regexp = "AUDITOR|TECHNICAL_EXPERT|BOTH") String expertType,
            @NotNull @Pattern(regexp = "FULLTIME|PARTTIME") String employmentType,
            UUID departmentId,
            @Size(max = 255) String position,
            LocalDate joinedDate,
            UUID homeLocationId,
            UUID userId,
            @DecimalMin("0") @DecimalMax("31") BigDecimal maxMandaysPerMonth) {}

    public record ExpertSummary(UUID id, String expertCode, String fullName, String expertType, String employmentType,
                                String status, String departmentName, String email, String phone,
                                OffsetDateTime updatedAt, LocalDate suspendedUntil) {}

    public record Counts(long educations, long experiences, long trainings, long certificates, long documents) {}

    public record ExpertDetail(UUID id, String expertCode, String fullName, LocalDate dateOfBirth, String gender,
                               String idNumber, String address, String phone, String email, String expertType,
                               String employmentType, UUID departmentId, String departmentName, String position,
                               LocalDate joinedDate, UUID homeLocationId, String homeLocationName, UUID userId,
                               String username, BigDecimal maxMandaysPerMonth, String status, String statusReason,
                               LocalDate suspendedUntil, List<String> availableActions, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               Counts counts) {}

    public record StatusRequest(@NotBlank @Pattern(regexp = "SUBMIT|REVIEW|APPROVE|RETURN|SUSPEND|REINSTATE|DEACTIVATE|REACTIVATE") String action,
                                @Size(max = 2000) String comment,
                                LocalDate suspendedUntil) {}

    public record HistoryEntry(OffsetDateTime at, String actor, String action, String fromStatus, String toStatus,
                               String comment) {}

    // ---- Sub-resources
    public record EducationRequest(@NotBlank String degreeLevelCode, UUID fieldId, @Size(max = 255) String major,
                                   @NotBlank @Size(max = 255) String institution,
                                   @Min(1950) @Max(2100) Short graduationYear, UUID evidenceDocumentId,
                                   Boolean verified) {}

    public record EducationDto(UUID id, String degreeLevelCode, String degreeLevelName, UUID fieldId, String fieldName,
                               String major, String institution, Short graduationYear, UUID evidenceDocumentId,
                               boolean verified) {}

    public record ExperienceRequest(UUID industryId, @NotBlank @Size(max = 255) String field,
                                    @Size(max = 255) String position, @Size(max = 255) String organization,
                                    @NotNull LocalDate fromDate, LocalDate toDate, boolean isCurrent,
                                    LocalDate verifiedUntil, String description, UUID evidenceDocumentId,
                                    List<UUID> codeIds) {}

    public record ExperienceDto(UUID id, UUID industryId, String industryName, String field, String position,
                                String organization, LocalDate fromDate, LocalDate toDate, boolean isCurrent,
                                LocalDate verifiedUntil, String description, UUID evidenceDocumentId, double years,
                                List<UUID> codeIds) {}

    public record TrainingRequest(@NotBlank @Size(max = 500) String trainingName, @Size(max = 255) String provider,
                                  UUID standardId,
                                  @Pattern(regexp = "LEAD_AUDITOR|INTERNAL_AUDITOR|TECHNICAL|CALIBRATION|REFRESHER|OTHER") String trainingType,
                                  LocalDate fromDate, LocalDate toDate,
                                  @DecimalMin("0") @DecimalMax("9999") BigDecimal hours, LocalDate validUntil,
                                  UUID certificateId, UUID evidenceDocumentId) {}

    public record TrainingDto(UUID id, String trainingName, String provider, UUID standardId, String standardCode,
                              String trainingType, LocalDate fromDate, LocalDate toDate, BigDecimal hours,
                              LocalDate validUntil, UUID certificateId, UUID evidenceDocumentId) {}

    public record CertificateRequest(@NotBlank @Size(max = 500) String certificateName,
                                     @Size(max = 100) String certificateNo, @Size(max = 255) String issuer,
                                     UUID standardId, LocalDate issuedDate, LocalDate expiryDate, UUID documentId,
                                     @Pattern(regexp = "VALID|EXPIRED|REVOKED") String status) {}

    public record CertificateDto(UUID id, String certificateName, String certificateNo, String issuer, UUID standardId,
                                 String standardCode, LocalDate issuedDate, LocalDate expiryDate, UUID documentId,
                                 String status, String expiryLevel, Long daysToExpiry) {}

    public record LanguageRequest(@NotBlank @Pattern(regexp = "BASIC|INTERMEDIATE|FLUENT|NATIVE") String proficiency,
                                  boolean canAudit) {}

    public record LanguageDto(String language, String proficiency, boolean canAudit) {}
}
