package com.npcore.ems.shared.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.web.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ngưỡng cảnh báo, timeout... cấu hình được (FR-17, BR-4.4.5) - không hard-code. */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SystemSetting> findAll() {
        return repository.findAll(org.springframework.data.domain.Sort.by("category", "key"));
    }

    @Cacheable(cacheNames = "settings", key = "#key")
    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return repository.findById(key).map(s -> s.getValue().asInt(defaultValue)).orElse(defaultValue);
    }

    @CacheEvict(cacheNames = "settings", allEntries = true)
    @Transactional
    public SystemSetting update(String key, JsonNode value) {
        SystemSetting s = repository.findById(key).orElseThrow(() -> ApiException.notFound("Cấu hình", key));
        validate(s.getValueType(), value);
        if ("INT".equals(s.getValueType()) && value.isTextual()) {
            value = com.fasterxml.jackson.databind.node.IntNode.valueOf(Integer.parseInt(value.asText()));
        }
        JsonNode old = s.getValue();
        s.setValue(value);
        s.setUpdatedAt(OffsetDateTime.now());
        s.setUpdatedBy(SecurityUtils.currentUser().id());
        auditService.record("UPDATE", "SYSTEM_SETTING", key, old, value, null);
        return s;
    }

    private static void validate(String type, JsonNode v) {
        boolean ok = switch (type) {
            case "INT" -> v.isInt() || (v.isTextual() && v.asText().matches("-?\\d+"));
            case "DECIMAL" -> v.isNumber();
            case "BOOLEAN" -> v.isBoolean();
            case "STRING" -> v.isTextual();
            default -> true;
        };
        if (!ok) throw ApiException.badRequest("Giá trị không đúng kiểu " + type);
    }
}
