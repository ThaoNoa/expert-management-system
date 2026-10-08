package com.npcore.ems.masterdata;

import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.importing.TabularFileParser;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.ImportResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * FR-3.1 Master Code: bộ mã có version theo scheme, cây phân cấp, import từ Excel/CSV.
 * Bộ mã chỉ sửa khi DRAFT; activate thì bộ ACTIVE cũ chuyển RETIRED (dữ liệu cũ giữ nguyên - BR-VER-004).
 */
@Service
@RequiredArgsConstructor
public class CodeSetService {

    private static final String CODE_PATTERN = "[A-Za-z0-9._-]{1,50}";

    private final CodeSetRepository codeSets;
    private final CodeRepository codes;
    private final SchemeRepository schemes;
    private final TabularFileParser parser;
    private final AuditService audit;

    public record CodeSetDto(UUID id, UUID schemeId, String schemeCode, String version, LocalDate effectiveFrom,
                             LocalDate effectiveTo, String sourceRef, String status, long codeCount) {}

    public record CodeSetRequest(@NotNull UUID schemeId, @NotBlank @Size(max = 50) String version,
                                 @NotNull LocalDate effectiveFrom, LocalDate effectiveTo, String sourceRef) {}

    public record CodeDto(UUID id, UUID codeSetId, String codeValue, String codeName, UUID parentId,
                          String parentCode, int level, String path, String riskCategory, String status) {}

    public record CodeRequest(@NotBlank @Pattern(regexp = CODE_PATTERN, message = "chỉ gồm chữ, số, . _ -") String codeValue,
                              @NotBlank @Size(max = 500) String codeName, String parentCode,
                              @Size(max = 20) String riskCategory) {}

    public record CodeUpdateRequest(@NotBlank @Size(max = 500) String codeName, @Size(max = 20) String riskCategory,
                                    @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    @Transactional(readOnly = true)
    public List<CodeSetDto> list(UUID schemeId) {
        Map<UUID, Long> counts = codes.countBySet().stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
        return codeSets.search(schemeId).stream().map(c -> toDto(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional
    public CodeSetDto create(CodeSetRequest r) {
        Scheme scheme = schemes.findById(r.schemeId()).orElseThrow(() -> ApiException.notFound("Scheme", r.schemeId()));
        if (codeSets.existsBySchemeIdAndVersionIgnoreCase(scheme.getId(), r.version().trim())) {
            throw ApiException.duplicate("Phiên bản bộ mã đã tồn tại trong scheme");
        }
        if (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom())) {
            throw ApiException.badRequest("Ngày hết hiệu lực phải sau ngày hiệu lực");
        }
        CodeSet cs = new CodeSet();
        cs.setScheme(scheme);
        cs.setVersion(r.version().trim());
        cs.setEffectiveFrom(r.effectiveFrom());
        cs.setEffectiveTo(r.effectiveTo());
        cs.setSourceRef(r.sourceRef());
        codeSets.saveAndFlush(cs);
        CodeSetDto dto = toDto(cs, 0);
        audit.record("CREATE", "CODE_SET", cs.getId(), null, dto, null);
        return dto;
    }

    @CacheEvict(cacheNames = "codes", allEntries = true)
    @Transactional
    public CodeSetDto activate(UUID id) {
        CodeSet cs = load(id);
        if (!"DRAFT".equals(cs.getStatus())) throw ApiException.businessRule("Chỉ kích hoạt được bộ mã ở trạng thái DRAFT");
        List<Code> list = codes.findByCodeSetIdOrderByPath(id);
        if (list.isEmpty()) throw ApiException.businessRule("Bộ mã chưa có code nào");
        codeSets.findBySchemeIdAndStatus(cs.getScheme().getId(), "ACTIVE").ifPresent(old -> {
            old.setStatus("RETIRED");
            if (old.getEffectiveTo() == null) old.setEffectiveTo(cs.getEffectiveFrom().minusDays(1));
            codeSets.flush();                                  // tránh vi phạm unique "1 bộ ACTIVE / scheme"
            audit.record("RETIRE", "CODE_SET", old.getId(), "ACTIVE", "RETIRED", "Thay bởi " + cs.getVersion());
        });
        cs.setStatus("ACTIVE");
        codeSets.flush();
        audit.record("ACTIVATE", "CODE_SET", id, "DRAFT", "ACTIVE", null);
        return toDto(cs, list.size());
    }

    @Cacheable(cacheNames = "codes", key = "#codeSetId")
    @Transactional(readOnly = true)
    public List<CodeDto> listCodes(UUID codeSetId) {
        load(codeSetId);
        List<Code> list = codes.findByCodeSetIdOrderByPath(codeSetId);
        Map<UUID, String> valueById = list.stream().collect(Collectors.toMap(Code::getId, Code::getValue));
        return list.stream().map(c -> toDto(c, valueById.get(c.getParentId()))).toList();
    }

    @CacheEvict(cacheNames = "codes", key = "#codeSetId")
    @Transactional
    public CodeDto addCode(UUID codeSetId, CodeRequest r) {
        CodeSet cs = loadDraft(codeSetId);
        Map<String, Code> existing = codes.findByCodeSetIdOrderByPath(codeSetId).stream()
                .collect(Collectors.toMap(c -> c.getValue().toUpperCase(Locale.ROOT), Function.identity()));
        Code c = buildCode(cs.getId(), r, existing);
        codes.saveAndFlush(c);
        String parentValue = c.getParentId() == null ? null : existing.values().stream()
                .filter(x -> x.getId().equals(c.getParentId())).map(Code::getValue).findFirst().orElse(null);
        CodeDto dto = toDto(c, parentValue);
        audit.record("CREATE", "CODE", c.getId(), null, dto, null);
        return dto;
    }

    @CacheEvict(cacheNames = "codes", allEntries = true)
    @Transactional
    public CodeDto updateCode(UUID codeId, CodeUpdateRequest r) {
        Code c = codes.findById(codeId).orElseThrow(() -> ApiException.notFound("Code", codeId));
        loadDraft(c.getCodeSetId());
        String parent = c.getParentId() == null ? null : codes.findById(c.getParentId()).map(Code::getValue).orElse(null);
        CodeDto before = toDto(c, parent);
        c.setName(r.codeName().trim());
        c.setRiskCategory(blankToNull(r.riskCategory()));
        if (r.status() != null) c.setStatus(r.status());
        codes.flush();
        CodeDto after = toDto(c, parent);
        audit.record("UPDATE", "CODE", codeId, before, after, null);
        return after;
    }

    /**
     * Import tất cả hoặc không gì cả: có lỗi ở bất kỳ dòng nào thì không ghi dòng nào.
     * Cột: code_value, code_name, parent_code, risk_category. Code cha có thể nằm sau code con trong file.
     */
    @CacheEvict(cacheNames = "codes", key = "#codeSetId")
    @Transactional
    public ImportResult importCodes(UUID codeSetId, MultipartFile file) {
        CodeSet cs = loadDraft(codeSetId);
        List<Map<String, String>> rows = parser.parse(file);
        ImportResult.Builder result = new ImportResult.Builder();
        Map<String, Code> known = new HashMap<>(codes.findByCodeSetIdOrderByPath(codeSetId).stream()
                .collect(Collectors.toMap(c -> c.getValue().toUpperCase(Locale.ROOT), Function.identity())));
        Map<Integer, CodeRequest> pending = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNo = i + 2;
            if (row.isEmpty()) continue;
            result.row();
            CodeRequest req = new CodeRequest(row.get("code_value"), row.get("code_name"), row.get("parent_code"),
                    row.get("risk_category"));
            String err = validateRow(req);
            if (err == null && known.containsKey(req.codeValue().toUpperCase(Locale.ROOT))) err = "Code đã tồn tại: " + req.codeValue();
            if (err == null && pending.values().stream().anyMatch(p -> p.codeValue().equalsIgnoreCase(req.codeValue()))) {
                err = "Code bị lặp trong file: " + req.codeValue();
            }
            if (err != null) result.error(rowNo, err);
            else pending.put(rowNo, req);
        }
        // Sắp thứ tự để code cha (dù nằm sau trong file) được tạo trước code con
        java.util.Set<String> values = new java.util.HashSet<>(known.keySet());
        List<CodeRequest> ordered = new ArrayList<>();
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
                CodeRequest req = it.next().getValue();
                String parent = blankToNull(req.parentCode());
                if (parent == null || values.contains(parent.toUpperCase(Locale.ROOT))) {
                    values.add(req.codeValue().trim().toUpperCase(Locale.ROOT));
                    ordered.add(req);
                    it.remove();
                    progress = true;
                }
            }
        }
        pending.forEach((rowNo, req) -> result.error(rowNo, "Code cha không tồn tại: " + req.parentCode()));
        if (result.hasErrors()) {
            ImportResult failed = result.build();
            return new ImportResult(failed.total(), 0, 0, failed.errors());
        }
        for (CodeRequest req : ordered) {
            Code c = buildCode(cs.getId(), req, known);
            codes.save(c);                                   // persist ngay để có id cho code con
            known.put(c.getValue().toUpperCase(Locale.ROOT), c);
            result.imported();
        }
        codes.flush();
        audit.record("IMPORT", "CODE_SET", codeSetId, null, Map.of("file", String.valueOf(file.getOriginalFilename()),
                "imported", ordered.size()), null);
        return result.build();
    }

    private static String validateRow(CodeRequest r) {
        if (r.codeValue() == null || !r.codeValue().matches(CODE_PATTERN)) return "code_value trống hoặc không hợp lệ";
        if (r.codeName() == null || r.codeName().isBlank()) return "code_name trống";
        if (r.codeName().length() > 500) return "code_name quá 500 ký tự";
        if (r.riskCategory() != null && r.riskCategory().trim().length() > 20) return "risk_category quá 20 ký tự";
        return null;
    }

    private Code buildCode(UUID codeSetId, CodeRequest r, Map<String, Code> existingByValue) {
        String value = r.codeValue().trim();
        if (existingByValue.containsKey(value.toUpperCase(Locale.ROOT))) throw ApiException.duplicate("Code đã tồn tại: " + value);
        Code c = new Code();
        c.setCodeSetId(codeSetId);
        c.setValue(value);
        c.setName(r.codeName().trim());
        c.setRiskCategory(blankToNull(r.riskCategory()));
        String parentCode = blankToNull(r.parentCode());
        if (parentCode == null) {
            c.setLevel((short) 1);
            c.setPath("/" + value + "/");
        } else {
            Code parent = existingByValue.get(parentCode.toUpperCase(Locale.ROOT));
            if (parent == null) throw ApiException.badRequest("Code cha không tồn tại: " + parentCode);
            c.setParentId(parent.getId());
            c.setLevel((short) (parent.getLevel() + 1));
            c.setPath(parent.getPath() + value + "/");
        }
        return c;
    }

    private CodeSet load(UUID id) {
        return codeSets.findById(id).orElseThrow(() -> ApiException.notFound("Bộ mã", id));
    }

    private CodeSet loadDraft(UUID id) {
        CodeSet cs = load(id);
        if (!"DRAFT".equals(cs.getStatus())) {
            throw ApiException.businessRule("Bộ mã đã " + cs.getStatus() + " - không sửa trực tiếp, hãy tạo phiên bản mới");
        }
        return cs;
    }

    @Transactional(readOnly = true)
    public List<CodeDto> listAllActiveCodes() {
        List<Code> list = codes.findAll().stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .toList();
        Map<UUID, String> valueById = list.stream().collect(Collectors.toMap(Code::getId, Code::getValue, (a, b) -> a));
        return list.stream().map(c -> toDto(c, valueById.get(c.getParentId()))).toList();
    }

    private static CodeSetDto toDto(CodeSet c, long count) {
        return new CodeSetDto(c.getId(), c.getScheme().getId(), c.getScheme().getCode(), c.getVersion(),
                c.getEffectiveFrom(), c.getEffectiveTo(), c.getSourceRef(), c.getStatus(), count);
    }

    private static CodeDto toDto(Code c, String parentCode) {
        return new CodeDto(c.getId(), c.getCodeSetId(), c.getValue(), c.getName(), c.getParentId(), parentCode,
                c.getLevel(), c.getPath(), c.getRiskCategory(), c.getStatus());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
