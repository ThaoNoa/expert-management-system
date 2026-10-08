package com.npcore.ems.competency;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CompetencyDtos {
    private CompetencyDtos() {}

    // ---- Competency Definition
    public record CompetencyDefinitionRequest(
            @NotNull UUID schemeId,
            @NotNull UUID standardId,
            UUID codeId,
            @NotNull UUID assessmentRoleId,
            Short defaultValidityMonths,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String version,
            @Pattern(regexp = "DRAFT|ACTIVE|RETIRED") String status,
            JsonNode criteria) {}

    public record CompetencyDefinitionDto(
            UUID id,
            UUID schemeId,
            String schemeCode,
            String schemeName,
            UUID standardId,
            String standardCode,
            String standardName,
            UUID codeId,
            String codeValue,
            String codeName,
            UUID assessmentRoleId,
            String roleCode,
            String roleName,
            Short defaultValidityMonths,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String version,
            String status,
            JsonNode criteria) {}

    // ---- Expert Competency
    public record ExpertCompetencyRequest(
            UUID competencyDefinitionId,
            UUID standardId,
            UUID codeId,
            UUID assessmentRoleId,
            UUID standardVersionId,
            @Pattern(regexp = "IN_TRAINING|QUALIFIED|SENIOR") String competencyLevel,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 2000) String notes) {}

    public record CompetencyActionRequest(
            @NotBlank String action,
            @Size(max = 2000) String comment) {}

    public record EvidenceRequest(
            UUID documentId,
            @NotBlank @Pattern(regexp = "EDUCATION|EXPERIENCE|TRAINING|CERTIFICATE|AUDIT_LOG|COMPETENCE_TEST|WITNESS|INTERVIEW|OTHER")
            String evidenceType,
            String sourceObjectType,
            UUID sourceObjectId,
            @Size(max = 1000) String description) {}

    public record EvidenceDto(
            UUID id,
            UUID expertCompetencyId,
            UUID documentId,
            String evidenceType,
            String sourceObjectType,
            UUID sourceObjectId,
            String description,
            OffsetDateTime createdAt) {}

    public record ExpertCompetencyDto(
            UUID id,
            UUID expertId,
            CompetencyDefinitionDto definition,
            UUID standardVersionId,
            String competencyLevel,
            String status,
            int revisionNo,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            LocalDate firstApprovedDate,
            UUID approvedBy,
            OffsetDateTime approvedAt,
            UUID submittedBy,
            OffsetDateTime submittedAt,
            String notes,
            OffsetDateTime createdAt,
            List<String> availableActions,
            List<EvidenceDto> evidences) {}

    // ---- Tạo hàng loạt định nghĩa năng lực (một tiêu chuẩn + một vai trò + nhiều code)
    public record BulkDefinitionRequest(
            @NotNull UUID standardId,
            @NotNull UUID assessmentRoleId,
            List<UUID> codeIds,
            boolean includeGeneral,
            Short defaultValidityMonths,
            @NotNull LocalDate effectiveFrom,
            String version,
            JsonNode criteria) {}

    public record BulkResult(int created, int skipped) {}

    // ---- Competency Matrix (dòng = chuyên gia, cột = code của tiêu chuẩn)
    /** Cột ma trận: codeValue "*" = năng lực toàn tiêu chuẩn (không theo code, VD Lead Auditor). */
    public record MatrixColumn(UUID codeId, String codeValue, String codeName, String parentCode) {}

    /**
     * Ô ma trận. inherited = có được nhờ code cha (scheme bật "code cha bao code con");
     * expired = đã quá ngày hiệu lực; expiringSoon = còn ≤ 60 ngày.
     */
    public record MatrixCell(
            UUID competencyId,
            UUID definitionId,
            String standardCode,
            String codeValue,
            String roleCode,
            String level,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean expired,
            boolean expiringSoon,
            boolean inherited) {}

    public record MatrixRow(
            UUID expertId,
            String expertCode,
            String expertName,
            String expertType,
            String employmentType,
            String expertStatus,
            LocalDate suspendedUntil,
            List<MatrixCell> cells) {}

    public record MatrixResponse(
            UUID standardId,
            String standardCode,
            String standardName,
            boolean parentCoversChild,
            List<MatrixColumn> columns,
            List<MatrixRow> rows) {}
}
