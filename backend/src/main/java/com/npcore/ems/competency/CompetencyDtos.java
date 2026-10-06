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

    // ---- Competency Matrix (tra cứu chéo chuyên gia x năng lực)
    public record MatrixCell(
            UUID competencyId,
            UUID definitionId,
            String standardCode,
            String codeValue,
            String roleCode,
            String level,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record MatrixRow(
            UUID expertId,
            String expertCode,
            String expertName,
            String expertType,
            String employmentType,
            List<MatrixCell> cells) {}
}
