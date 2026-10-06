package com.npcore.ems.expert;

import com.npcore.ems.shared.security.SecurityUtils;

import com.npcore.ems.expert.ExpertDtos.CertificateDto;
import com.npcore.ems.expert.ExpertDtos.CertificateRequest;
import com.npcore.ems.expert.ExpertDtos.EducationDto;
import com.npcore.ems.expert.ExpertDtos.EducationRequest;
import com.npcore.ems.expert.ExpertDtos.ExperienceDto;
import com.npcore.ems.expert.ExpertDtos.ExperienceRequest;
import com.npcore.ems.expert.ExpertDtos.TrainingDto;
import com.npcore.ems.expert.ExpertDtos.TrainingRequest;
import com.npcore.ems.masterdata.CodeRepository;
import com.npcore.ems.masterdata.DegreeLevel;
import com.npcore.ems.masterdata.DegreeLevelRepository;
import com.npcore.ems.masterdata.EducationField;
import com.npcore.ems.masterdata.EducationFieldRepository;
import com.npcore.ems.masterdata.Industry;
import com.npcore.ems.masterdata.IndustryRepository;
import com.npcore.ems.masterdata.Standard;
import com.npcore.ems.masterdata.StandardRepository;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.settings.SettingsService;
import com.npcore.ems.shared.web.ApiException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Học vấn, kinh nghiệm, đào tạo, chứng chỉ (FR-2.2 .. 2.5). */
public final class ExpertProfileServices {
    private ExpertProfileServices() {}

    // ---------------- Education (FR-2.2)
    @Service
    public static class EducationService extends ExpertChildService<ExpertEducation, EducationDto, EducationRequest> {
        private final ExpertEducationRepository repo;
        private final DegreeLevelRepository degrees;
        private final EducationFieldRepository fields;

        public EducationService(ExpertAccess access, AuditService audit, ExpertEducationRepository repo,
                                DegreeLevelRepository degrees, EducationFieldRepository fields) {
            super(access, audit, repo, "EXPERT_EDUCATION", "Học vấn");
            this.repo = repo;
            this.degrees = degrees;
            this.fields = fields;
        }

        @Override protected List<ExpertEducation> findByExpert(UUID expertId) { return repo.findByExpertId(expertId); }
        @Override protected ExpertEducation newEntity() { return new ExpertEducation(); }

        @Override
        protected void apply(ExpertEducation e, EducationRequest r) {
            if (!degrees.existsById(r.degreeLevelCode())) throw ApiException.badRequest("Trình độ không hợp lệ: " + r.degreeLevelCode());
            if (r.fieldId() != null && !fields.existsById(r.fieldId())) throw ApiException.notFound("Lĩnh vực đào tạo", r.fieldId());
            e.setDegreeLevelCode(r.degreeLevelCode());
            e.setFieldId(r.fieldId());
            e.setMajor(r.major());
            e.setInstitution(r.institution().trim());
            e.setGraduationYear(r.graduationYear());
            e.setEvidenceDocumentId(r.evidenceDocumentId());
            // chỉ người có quyền trên toàn bộ dữ liệu mới xác nhận học vấn
            if (r.verified() != null && SecurityUtils.currentUser().hasAll("EXPERT_COMPETENCY_EDIT")) e.setVerified(r.verified());
        }

        @Override
        protected EducationDto toDto(ExpertEducation e) {
            return new EducationDto(e.getId(), e.getDegreeLevelCode(),
                    degrees.findById(e.getDegreeLevelCode()).map(DegreeLevel::getName).orElse(null), e.getFieldId(),
                    e.getFieldId() == null ? null : fields.findById(e.getFieldId()).map(EducationField::getName).orElse(null),
                    e.getMajor(), e.getInstitution(), e.getGraduationYear(), e.getEvidenceDocumentId(), e.isVerified());
        }
    }

    // ---------------- Experience (FR-2.3)
    @Service
    public static class ExperienceService extends ExpertChildService<ExpertExperience, ExperienceDto, ExperienceRequest> {
        private final ExpertExperienceRepository repo;
        private final IndustryRepository industries;
        private final CodeRepository codes;

        public ExperienceService(ExpertAccess access, AuditService audit, ExpertExperienceRepository repo,
                                 IndustryRepository industries, CodeRepository codes) {
            super(access, audit, repo, "EXPERT_EXPERIENCE", "Kinh nghiệm");
            this.repo = repo;
            this.industries = industries;
            this.codes = codes;
        }

        @Override protected List<ExpertExperience> findByExpert(UUID expertId) {
            return repo.findByExpertId(expertId).stream()
                    .sorted((a, b) -> b.getFromDate().compareTo(a.getFromDate())).toList();
        }
        @Override protected ExpertExperience newEntity() { return new ExpertExperience(); }

        @Override
        protected void apply(ExpertExperience e, ExperienceRequest r) {
            if (r.isCurrent() && r.toDate() != null) throw ApiException.badRequest("Kinh nghiệm đang làm thì không có ngày kết thúc");
            if (!r.isCurrent() && r.toDate() == null) throw ApiException.badRequest("Kinh nghiệm đã kết thúc phải có ngày kết thúc");
            if (r.toDate() != null && r.toDate().isBefore(r.fromDate())) throw ApiException.badRequest("Ngày kết thúc phải sau ngày bắt đầu");
            if (r.fromDate().isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày bắt đầu không được ở tương lai");
            if (r.industryId() != null && !industries.existsById(r.industryId())) throw ApiException.notFound("Ngành", r.industryId());
            List<UUID> codeIds = r.codeIds() == null ? List.of() : r.codeIds();
            if (codes.findAllById(codeIds).size() != new HashSet<>(codeIds).size()) throw ApiException.badRequest("Có code không tồn tại");
            e.setIndustryId(r.industryId());
            e.setField(r.field().trim());
            e.setPosition(r.position());
            e.setOrganization(r.organization());
            e.setFromDate(r.fromDate());
            e.setToDate(r.toDate());
            e.setCurrent(r.isCurrent());
            // BR-DATA-003: đang làm → số năm chỉ tính tới mốc đã xác nhận; mặc định là ngày khai báo, không tự tăng
            LocalDate verified = r.isCurrent() ? (r.verifiedUntil() != null ? r.verifiedUntil()
                    : (e.getVerifiedUntil() != null ? e.getVerifiedUntil() : LocalDate.now())) : null;
            if (verified != null && verified.isAfter(LocalDate.now())) throw ApiException.badRequest("Mốc xác nhận không được ở tương lai");
            e.setVerifiedUntil(verified);
            e.setDescription(r.description());
            e.setEvidenceDocumentId(r.evidenceDocumentId());
            e.getCodeIds().clear();
            e.getCodeIds().addAll(codeIds);
        }

        @Override
        protected ExperienceDto toDto(ExpertExperience e) {
            return new ExperienceDto(e.getId(), e.getIndustryId(),
                    e.getIndustryId() == null ? null : industries.findById(e.getIndustryId()).map(Industry::getName).orElse(null),
                    e.getField(), e.getPosition(), e.getOrganization(), e.getFromDate(), e.getToDate(), e.isCurrent(),
                    e.getVerifiedUntil(), e.getDescription(), e.getEvidenceDocumentId(), e.years(), List.copyOf(e.getCodeIds()));
        }
    }

    // ---------------- Training (FR-2.4)
    @Service
    public static class TrainingService extends ExpertChildService<ExpertTraining, TrainingDto, TrainingRequest> {
        private final ExpertTrainingRepository repo;
        private final StandardRepository standards;
        private final ExpertCertificateRepository certificates;

        public TrainingService(ExpertAccess access, AuditService audit, ExpertTrainingRepository repo,
                               StandardRepository standards, ExpertCertificateRepository certificates) {
            super(access, audit, repo, "EXPERT_TRAINING", "Đào tạo");
            this.repo = repo;
            this.standards = standards;
            this.certificates = certificates;
        }

        @Override protected List<ExpertTraining> findByExpert(UUID expertId) { return repo.findByExpertId(expertId); }
        @Override protected ExpertTraining newEntity() { return new ExpertTraining(); }

        @Override
        protected void apply(ExpertTraining e, TrainingRequest r) {
            if (r.fromDate() != null && r.toDate() != null && r.toDate().isBefore(r.fromDate())) {
                throw ApiException.badRequest("Ngày kết thúc phải sau ngày bắt đầu");
            }
            if (r.standardId() != null && !standards.existsById(r.standardId())) throw ApiException.notFound("Tiêu chuẩn", r.standardId());
            if (r.certificateId() != null && certificates.findById(r.certificateId())
                    .filter(c -> c.getExpertId().equals(e.getExpertId())).isEmpty()) {
                throw ApiException.badRequest("Chứng chỉ không thuộc chuyên gia này");
            }
            e.setTrainingName(r.trainingName().trim());
            e.setProvider(r.provider());
            e.setStandardId(r.standardId());
            e.setTrainingType(r.trainingType());
            e.setFromDate(r.fromDate());
            e.setToDate(r.toDate());
            e.setHours(r.hours());
            e.setValidUntil(r.validUntil());
            e.setCertificateId(r.certificateId());
            e.setEvidenceDocumentId(r.evidenceDocumentId());
        }

        @Override
        protected TrainingDto toDto(ExpertTraining e) {
            return new TrainingDto(e.getId(), e.getTrainingName(), e.getProvider(), e.getStandardId(),
                    e.getStandardId() == null ? null : standards.findById(e.getStandardId()).map(Standard::getCode).orElse(null),
                    e.getTrainingType(), e.getFromDate(), e.getToDate(), e.getHours(), e.getValidUntil(),
                    e.getCertificateId(), e.getEvidenceDocumentId());
        }
    }

    // ---------------- Certificate (FR-2.5, BR-2.5.1/2.5.2)
    @Service
    public static class CertificateService extends ExpertChildService<ExpertCertificate, CertificateDto, CertificateRequest> {
        private final ExpertCertificateRepository repo;
        private final StandardRepository standards;
        private final SettingsService settings;

        public CertificateService(ExpertAccess access, AuditService audit, ExpertCertificateRepository repo,
                                  StandardRepository standards, SettingsService settings) {
            super(access, audit, repo, "EXPERT_CERTIFICATE", "Chứng chỉ");
            this.repo = repo;
            this.standards = standards;
            this.settings = settings;
        }

        @Override protected List<ExpertCertificate> findByExpert(UUID expertId) { return repo.findByExpertId(expertId); }
        @Override protected ExpertCertificate newEntity() { return new ExpertCertificate(); }

        @Override
        protected void apply(ExpertCertificate e, CertificateRequest r) {
            if (r.issuedDate() != null && r.expiryDate() != null && r.expiryDate().isBefore(r.issuedDate())) {
                throw ApiException.badRequest("Ngày hết hạn phải sau ngày cấp");
            }
            if (r.standardId() != null && !standards.existsById(r.standardId())) throw ApiException.notFound("Tiêu chuẩn", r.standardId());
            e.setCertificateName(r.certificateName().trim());
            e.setCertificateNo(r.certificateNo());
            e.setIssuer(r.issuer());
            e.setStandardId(r.standardId());
            e.setIssuedDate(r.issuedDate());
            e.setExpiryDate(r.expiryDate());
            e.setDocumentId(r.documentId());
            if (r.status() != null) e.setStatus(r.status());
        }

        @Override
        protected CertificateDto toDto(ExpertCertificate e) {
            Long days = e.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), e.getExpiryDate());
            return new CertificateDto(e.getId(), e.getCertificateName(), e.getCertificateNo(), e.getIssuer(),
                    e.getStandardId(),
                    e.getStandardId() == null ? null : standards.findById(e.getStandardId()).map(Standard::getCode).orElse(null),
                    e.getIssuedDate(), e.getExpiryDate(), e.getDocumentId(), e.getStatus(), expiryLevel(days), days);
        }

        /** BR-4.4.1..3: ngưỡng 60/30/7 ngày lấy từ cấu hình. */
        String expiryLevel(Long days) {
            if (days == null) return "NONE";
            if (days < 0) return "EXPIRED";
            if (days <= settings.getInt("alert.expiry.criticalDays", 7)) return "CRITICAL";
            if (days <= settings.getInt("alert.expiry.highDays", 30)) return "HIGH";
            if (days <= settings.getInt("alert.expiry.warningDays", 60)) return "WARNING";
            return "NONE";
        }
    }
}
