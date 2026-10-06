package com.npcore.ems.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DocumentDtos {
    private DocumentDtos() {}

    public record VersionDto(UUID id, int versionNo, String fileName, String contentType, long fileSize, String sha256,
                             LocalDate issuedDate, LocalDate expiryDate, String status, UUID uploadedBy,
                             OffsetDateTime uploadedAt, UUID verifiedBy, OffsetDateTime verifiedAt,
                             String rejectReason) {
        static VersionDto from(DocumentVersion v) {
            if (v == null) return null;
            return new VersionDto(v.getId(), v.getVersionNo(), v.getFileName(), v.getContentType(), v.getFileSize(),
                    v.getSha256(), v.getIssuedDate(), v.getExpiryDate(), v.getStatus(), v.getUploadedBy(),
                    v.getUploadedAt(), v.getVerifiedBy(), v.getVerifiedAt(), v.getRejectReason());
        }
    }

    public record LinkDto(UUID id, String objectType, UUID objectId, String purpose, OffsetDateTime linkedAt) {
        static LinkDto from(DocumentLink l) {
            return new LinkDto(l.getId(), l.getObjectType(), l.getObjectId(), l.getPurpose(), l.getLinkedAt());
        }
    }

    public record DocumentSummary(UUID id, String title, String documentTypeCode, String documentTypeName,
                                  UUID ownerExpertId, String ownerExpertName, VersionDto currentVersion,
                                  String status, OffsetDateTime createdAt) {}

    public record DocumentDetail(UUID id, String title, String documentTypeCode, String documentTypeName,
                                 UUID ownerExpertId, String ownerExpertName, VersionDto currentVersion,
                                 String status, OffsetDateTime createdAt, List<VersionDto> versions,
                                 List<LinkDto> links) {}

    public record UploadResult(boolean duplicate, DocumentDetail document) {}

    public record LinkRequest(@NotBlank String objectType, @NotNull UUID objectId, String purpose) {}

    public record RejectRequest(@NotBlank String reason) {}

    public record DocumentTypeDto(String code, String name, boolean requiresExpiry, boolean requiresVerification) {}
}
