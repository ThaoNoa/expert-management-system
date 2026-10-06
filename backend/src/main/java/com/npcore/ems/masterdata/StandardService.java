package com.npcore.ems.masterdata;

import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.web.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StandardService {

    private final StandardRepository standards;
    private final StandardVersionRepository versions;
    private final SchemeRepository schemes;
    private final AuditService audit;

    public record StandardDto(UUID id, String standardCode, String standardName, UUID schemeId, String schemeCode,
                              String status) {
        static StandardDto from(Standard s) {
            return new StandardDto(s.getId(), s.getCode(), s.getName(), s.getScheme().getId(), s.getScheme().getCode(),
                    s.getStatus());
        }
    }

    public record StandardRequest(@NotBlank @Size(max = 50) String standardCode, @NotBlank String standardName,
                                  @NotNull UUID schemeId, @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public record VersionDto(UUID id, UUID standardId, String version, LocalDate effectiveFrom, LocalDate effectiveTo,
                             LocalDate transitionEnd, String status) {
        static VersionDto from(StandardVersion v) {
            return new VersionDto(v.getId(), v.getStandardId(), v.getVersion(), v.getEffectiveFrom(),
                    v.getEffectiveTo(), v.getTransitionEnd(), v.getStatus());
        }
    }

    public record VersionRequest(@NotBlank @Size(max = 50) String version, @NotNull LocalDate effectiveFrom,
                                 LocalDate effectiveTo, LocalDate transitionEnd,
                                 @Pattern(regexp = "DRAFT|ACTIVE|TRANSITION|WITHDRAWN") String status) {}

    @Cacheable(cacheNames = "standards", key = "#schemeId == null ? 'all' : #schemeId")
    @Transactional(readOnly = true)
    public List<StandardDto> list(UUID schemeId) {
        return standards.search(schemeId).stream().map(StandardDto::from).toList();
    }

    @CacheEvict(cacheNames = "standards", allEntries = true)
    @Transactional
    public StandardDto create(StandardRequest r) {
        standards.findByCodeIgnoreCase(r.standardCode().trim()).ifPresent(s -> {
            throw ApiException.duplicate("Mã tiêu chuẩn đã tồn tại");
        });
        Standard s = new Standard();
        apply(s, r);
        standards.saveAndFlush(s);
        StandardDto dto = StandardDto.from(s);
        audit.record("CREATE", "STANDARD", s.getId(), null, dto, null);
        return dto;
    }

    @CacheEvict(cacheNames = "standards", allEntries = true)
    @Transactional
    public StandardDto update(UUID id, StandardRequest r) {
        Standard s = standards.findById(id).orElseThrow(() -> ApiException.notFound("Tiêu chuẩn", id));
        standards.findByCodeIgnoreCase(r.standardCode().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.duplicate("Mã tiêu chuẩn đã tồn tại");
        });
        StandardDto before = StandardDto.from(s);
        apply(s, r);
        standards.flush();
        StandardDto after = StandardDto.from(s);
        audit.record("UPDATE", "STANDARD", id, before, after, null);
        return after;
    }

    @Transactional(readOnly = true)
    public List<VersionDto> versions(UUID standardId) {
        ensureStandard(standardId);
        return versions.findByStandardIdOrderByEffectiveFromDesc(standardId).stream().map(VersionDto::from).toList();
    }

    @Transactional
    public VersionDto addVersion(UUID standardId, VersionRequest r) {
        ensureStandard(standardId);
        if (versions.existsByStandardIdAndVersionIgnoreCase(standardId, r.version().trim())) {
            throw ApiException.duplicate("Phiên bản đã tồn tại");
        }
        StandardVersion v = new StandardVersion();
        v.setStandardId(standardId);
        applyVersion(v, r);
        versions.saveAndFlush(v);
        VersionDto dto = VersionDto.from(v);
        audit.record("CREATE", "STANDARD_VERSION", v.getId(), null, dto, null);
        return dto;
    }

    @Transactional
    public VersionDto updateVersion(UUID versionId, VersionRequest r) {
        StandardVersion v = versions.findById(versionId).orElseThrow(() -> ApiException.notFound("Phiên bản", versionId));
        VersionDto before = VersionDto.from(v);
        applyVersion(v, r);
        versions.flush();
        VersionDto after = VersionDto.from(v);
        audit.record("UPDATE", "STANDARD_VERSION", versionId, before, after, null);
        return after;
    }

    private void apply(Standard s, StandardRequest r) {
        s.setCode(r.standardCode().trim());
        s.setName(r.standardName().trim());
        s.setScheme(schemes.findById(r.schemeId()).orElseThrow(() -> ApiException.notFound("Scheme", r.schemeId())));
        if (r.status() != null) s.setStatus(r.status());
    }

    private static void applyVersion(StandardVersion v, VersionRequest r) {
        if (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom())) {
            throw ApiException.badRequest("Ngày hết hiệu lực phải sau ngày hiệu lực");
        }
        v.setVersion(r.version().trim());
        v.setEffectiveFrom(r.effectiveFrom());
        v.setEffectiveTo(r.effectiveTo());
        v.setTransitionEnd(r.transitionEnd());
        if (r.status() != null) v.setStatus(r.status());
    }

    private void ensureStandard(UUID id) {
        if (!standards.existsById(id)) throw ApiException.notFound("Tiêu chuẩn", id);
    }
}
