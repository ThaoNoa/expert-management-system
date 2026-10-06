package com.npcore.ems.desktop.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTO khớp hợp đồng API (docs/api-phase1.md). */
public final class Dtos {
    private Dtos() {}

    // ---------------- Auth
    public record Me(UUID id, String username, String fullName, String email, List<String> roles,
                     List<String> permissions, UUID expertId) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, Me user) {}

    public record ImportResult(int total, int imported, int skipped, List<RowError> errors) {
        public record RowError(int row, String message) {}
    }

    // ---------------- Users / roles
    public record User(UUID id, String username, String email, String fullName, UUID departmentId,
                       String departmentName, String position, String status, List<String> roleCodes,
                       OffsetDateTime lastLoginAt, OffsetDateTime createdAt) {}

    public record CreateUserRequest(String username, String email, String fullName, UUID departmentId,
                                    String position, String password, List<String> roleCodes) {}

    public record UpdateUserRequest(String email, String fullName, UUID departmentId, String position,
                                    List<String> roleCodes) {}

    public record Grant(String code, String dataScope) {}

    public record Role(UUID id, String roleCode, String roleName, String description, boolean system,
                       List<Grant> permissions) {}

    public record RoleRequest(String roleCode, String roleName, String description, List<Grant> permissions) {}

    public record Permission(String code, String name, String module) {}

    // ---------------- Master data
    public record Department(UUID id, String departmentCode, String departmentName, UUID parentId, String status) {}

    public record AssessmentRole(UUID id, String roleCode, String roleName, String description,
                                 boolean countsForCoverage, int sortOrder, String status) {}

    public record Scheme(UUID id, String schemeCode, String schemeName, String description,
                         boolean parentCoversChild, String status) {}

    public record Standard(UUID id, String standardCode, String standardName, UUID schemeId, String schemeCode,
                           String status) {}

    public record StandardVersion(UUID id, UUID standardId, String version, LocalDate effectiveFrom,
                                  LocalDate effectiveTo, LocalDate transitionEnd, String status) {}

    public record CodeSet(UUID id, UUID schemeId, String schemeCode, String version, LocalDate effectiveFrom,
                          LocalDate effectiveTo, String sourceRef, String status, long codeCount) {}

    public record Code(UUID id, UUID codeSetId, String codeValue, String codeName, UUID parentId, String parentCode,
                       int level, String path, String riskCategory, String status) {}

    public record Industry(UUID id, String industryCode, String industryName, String description) {}

    public record Activity(UUID id, String activityCode, String activityName, String description) {}

    public record Location(UUID id, String locationName, String province, String country, String region,
                           BigDecimal latitude, BigDecimal longitude) {}

    public record EducationField(UUID id, String fieldCode, String fieldName, UUID parentId) {}

    public record DegreeLevel(String code, String name, int rankOrder) {}

    public record DocumentType(String code, String name, boolean requiresExpiry, boolean requiresVerification) {}

    // ---------------- Documents
    public record Version(UUID id, int versionNo, String fileName, String contentType, long fileSize, String sha256,
                          LocalDate issuedDate, LocalDate expiryDate, String status, UUID uploadedBy,
                          OffsetDateTime uploadedAt, UUID verifiedBy, OffsetDateTime verifiedAt, String rejectReason) {}

    public record Link(UUID id, String objectType, UUID objectId, String purpose, OffsetDateTime linkedAt) {}

    public record DocumentSummary(UUID id, String title, String documentTypeCode, String documentTypeName,
                                  UUID ownerExpertId, String ownerExpertName, Version currentVersion, String status,
                                  OffsetDateTime createdAt) {}

    public record DocumentDetail(UUID id, String title, String documentTypeCode, String documentTypeName,
                                 UUID ownerExpertId, String ownerExpertName, Version currentVersion, String status,
                                 OffsetDateTime createdAt, List<Version> versions, List<Link> links) {}

    public record UploadResult(boolean duplicate, DocumentDetail document) {}

    // ---------------- Experts
    public record ExpertRequest(String fullName, LocalDate dateOfBirth, String gender, String idNumber, String address,
                                String phone, String email, String expertType, String employmentType,
                                UUID departmentId, String position, LocalDate joinedDate, UUID homeLocationId,
                                UUID userId, BigDecimal maxMandaysPerMonth) {}

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
                               Counts counts) {
        public ExpertRequest toRequest() {
            return new ExpertRequest(fullName, dateOfBirth, gender, idNumber, address, phone, email, expertType,
                    employmentType, departmentId, position, joinedDate, homeLocationId, userId, maxMandaysPerMonth);
        }
    }

    public record HistoryEntry(OffsetDateTime at, String actor, String action, String fromStatus, String toStatus,
                               String comment) {}

    public record Education(UUID id, String degreeLevelCode, String degreeLevelName, UUID fieldId, String fieldName,
                            String major, String institution, Short graduationYear, UUID evidenceDocumentId,
                            boolean verified) {}

    public record EducationRequest(String degreeLevelCode, UUID fieldId, String major, String institution,
                                   Short graduationYear, UUID evidenceDocumentId, Boolean verified) {}

    public record Experience(UUID id, UUID industryId, String industryName, String field, String position,
                             String organization, LocalDate fromDate, LocalDate toDate,
                             @JsonProperty("isCurrent") boolean isCurrent, LocalDate verifiedUntil, String description, UUID evidenceDocumentId, double years,
                             List<UUID> codeIds) {}

    public record ExperienceRequest(UUID industryId, String field, String position, String organization,
                                    LocalDate fromDate, LocalDate toDate, @JsonProperty("isCurrent") boolean isCurrent,
                                    LocalDate verifiedUntil, String description, UUID evidenceDocumentId, List<UUID> codeIds) {}

    public record Training(UUID id, String trainingName, String provider, UUID standardId, String standardCode,
                           String trainingType, LocalDate fromDate, LocalDate toDate, BigDecimal hours,
                           LocalDate validUntil, UUID certificateId, UUID evidenceDocumentId) {}

    public record TrainingRequest(String trainingName, String provider, UUID standardId, String trainingType,
                                  LocalDate fromDate, LocalDate toDate, BigDecimal hours, LocalDate validUntil,
                                  UUID certificateId, UUID evidenceDocumentId) {}

    public record Certificate(UUID id, String certificateName, String certificateNo, String issuer, UUID standardId,
                              String standardCode, LocalDate issuedDate, LocalDate expiryDate, UUID documentId,
                              String status, String expiryLevel, Long daysToExpiry) {}

    public record CertificateRequest(String certificateName, String certificateNo, String issuer, UUID standardId,
                                     LocalDate issuedDate, LocalDate expiryDate, UUID documentId, String status) {}

    public record Language(String language, String proficiency, boolean canAudit) {}

    public record LanguageRequest(String proficiency, boolean canAudit) {}

    // ---------------- Audit / settings
    public record AuditLog(Long id, OffsetDateTime occurredAt, UUID userId, String username, String action,
                           String objectType, String objectId, JsonNode fromValue, JsonNode toValue, String reason,
                           String ipAddress) {}

    public record Setting(String key, JsonNode value, String valueType, String category, String description) {}

    // ---------------- Competency
    public record CompetencyDefinition(UUID id, UUID schemeId, String schemeCode, String schemeName,
                                      UUID standardId, String standardCode, String standardName,
                                      UUID codeId, String codeValue, String codeName,
                                      UUID assessmentRoleId, String roleCode, String roleName,
                                      Short defaultValidityMonths, LocalDate effectiveFrom, LocalDate effectiveTo,
                                      String version, String status, JsonNode criteria) {}

    public record CompetencyDefinitionRequest(UUID schemeId, UUID standardId, UUID codeId, UUID assessmentRoleId,
                                             Short defaultValidityMonths, LocalDate effectiveFrom, LocalDate effectiveTo,
                                             String version, String status, JsonNode criteria) {}

    public record Evidence(UUID id, UUID expertCompetencyId, UUID documentId, String evidenceType,
                           String sourceObjectType, UUID sourceObjectId, String description,
                           OffsetDateTime createdAt) {}

    public record EvidenceRequest(UUID documentId, String evidenceType, String sourceObjectType,
                                 UUID sourceObjectId, String description) {}

    public record ExpertCompetency(UUID id, UUID expertId, CompetencyDefinition definition, UUID standardVersionId,
                                  String competencyLevel, String status, int revisionNo, LocalDate effectiveFrom,
                                  LocalDate effectiveTo, LocalDate firstApprovedDate, UUID approvedBy,
                                  OffsetDateTime approvedAt, UUID submittedBy, OffsetDateTime submittedAt,
                                  String notes, OffsetDateTime createdAt, List<String> availableActions,
                                  List<Evidence> evidences) {}

    public record ExpertCompetencyRequest(UUID competencyDefinitionId, UUID standardId, UUID codeId,
                                         UUID assessmentRoleId, UUID standardVersionId, String competencyLevel,
                                         LocalDate effectiveFrom, LocalDate effectiveTo, String notes) {}

    public record CompetencyActionRequest(String action, String comment) {}

    public record MatrixCell(UUID competencyId, UUID definitionId, String standardCode, String codeValue,
                            String roleCode, String level, String status, LocalDate effectiveFrom,
                            LocalDate effectiveTo) {}

    public record MatrixRow(UUID expertId, String expertCode, String expertName, String expertType,
                            String employmentType, List<MatrixCell> cells) {}
}
