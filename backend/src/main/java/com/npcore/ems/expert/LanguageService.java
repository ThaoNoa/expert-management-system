package com.npcore.ems.expert;

import com.npcore.ems.expert.ExpertDtos.LanguageDto;
import com.npcore.ems.expert.ExpertDtos.LanguageRequest;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.web.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-2.6 ngôn ngữ (khoá = expert + mã ISO 639-1). */
@Service
@RequiredArgsConstructor
public class LanguageService {

    private final ExpertLanguageRepository repo;
    private final ExpertAccess access;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<LanguageDto> list(UUID expertId) {
        access.requireView(expertId);
        return repo.findByExpertIdOrderByLanguage(expertId).stream().map(LanguageService::toDto).toList();
    }

    @Transactional
    public LanguageDto upsert(UUID expertId, String language, LanguageRequest r) {
        access.requireEdit(expertId);
        String lang = language.trim().toLowerCase(Locale.ROOT);
        if (!lang.matches("[a-z]{2}")) throw ApiException.badRequest("Mã ngôn ngữ phải theo ISO 639-1 (VD: vi, en)");
        ExpertLanguage l = repo.findById(new ExpertLanguage.Key(expertId, lang)).orElseGet(() -> {
            ExpertLanguage n = new ExpertLanguage();
            n.setExpertId(expertId);
            n.setLanguage(lang);
            return n;
        });
        LanguageDto before = l.getProficiency() == null ? null : toDto(l);
        l.setProficiency(r.proficiency());
        l.setCanAudit(r.canAudit());
        repo.saveAndFlush(l);
        LanguageDto after = toDto(l);
        audit.record(before == null ? "CREATE" : "UPDATE", "EXPERT_LANGUAGE", expertId, before, after, lang);
        return after;
    }

    @Transactional
    public void delete(UUID expertId, String language) {
        access.requireEdit(expertId);
        ExpertLanguage l = repo.findById(new ExpertLanguage.Key(expertId, language.toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> ApiException.notFound("Ngôn ngữ", language));
        audit.record("DELETE", "EXPERT_LANGUAGE", expertId, toDto(l), null, language);
        repo.delete(l);
    }

    private static LanguageDto toDto(ExpertLanguage l) {
        return new LanguageDto(l.getLanguage(), l.getProficiency(), l.isCanAudit());
    }
}
