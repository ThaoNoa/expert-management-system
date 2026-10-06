package com.npcore.ems.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository repository;

    public record AuditLogDto(Long id, OffsetDateTime occurredAt, UUID userId, String username, String action,
                              String objectType, String objectId, JsonNode fromValue, JsonNode toValue,
                              String reason, String ipAddress) {
        static AuditLogDto from(AuditLog a) {
            return new AuditLogDto(a.getId(), a.getOccurredAt(), a.getUserId(), a.getUsername(), a.getAction(),
                    a.getObjectType(), a.getObjectId(), a.getFromValue(), a.getToValue(), a.getReason(),
                    a.getIpAddress() == null ? null : a.getIpAddress().getHostAddress());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_LOG_VIEW')")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(@RequestParam(required = false) String objectType,
                                            @RequestParam(required = false) String objectId,
                                            @RequestParam(required = false) UUID userId,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (objectType != null) ps.add(cb.equal(root.get("objectType"), objectType));
            if (objectId != null) ps.add(cb.equal(root.get("objectId"), objectId));
            if (userId != null) ps.add(cb.equal(root.get("userId"), userId));
            if (action != null) ps.add(cb.equal(root.get("action"), action));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay().atOffset(ZoneOffset.UTC)));
            if (to != null) ps.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(repository.findAll(spec, pageable), AuditLogDto::from);
    }
}
