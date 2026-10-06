package com.npcore.ems.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.npcore.ems.shared.security.CurrentUser;
import com.npcore.ems.shared.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi audit log (BR-AUD-004: who, what, when, from, to, why, IP, object).
 * Ghi trong cùng transaction với thay đổi nghiệp vụ: rollback thì log cũng rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(String action, String objectType, Object objectId, Object from, Object to, String reason) {
        AuditLog entry = new AuditLog();
        entry.setOccurredAt(OffsetDateTime.now());
        CurrentUser user = SecurityUtils.currentUserOptional().orElse(null);
        if (user != null) {
            entry.setUserId(user.id());
            entry.setUsername(user.username());
        }
        entry.setAction(action);
        entry.setObjectType(objectType);
        entry.setObjectId(objectId == null ? null : objectId.toString());
        entry.setFromValue(toJson(from));
        entry.setToValue(toJson(to));
        entry.setReason(reason);
        entry.setCorrelationId(MDC.get("correlationId"));
        HttpServletRequest req = currentRequest();
        if (req != null) {
            entry.setIpAddress(parseIp(clientIp(req)));
            String ua = req.getHeader("User-Agent");
            entry.setUserAgent(ua == null ? null : ua.substring(0, Math.min(500, ua.length())));
        }
        repository.save(entry);
    }

    /** Ghi log cho hành động không gắn user hiện tại (VD đăng nhập thất bại). */
    @Transactional
    public void recordAs(java.util.UUID userId, String username, String action, String objectType, Object objectId,
                         String reason) {
        AuditLog entry = new AuditLog();
        entry.setOccurredAt(OffsetDateTime.now());
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setAction(action);
        entry.setObjectType(objectType);
        entry.setObjectId(objectId == null ? null : objectId.toString());
        entry.setReason(reason);
        HttpServletRequest req = currentRequest();
        if (req != null) entry.setIpAddress(parseIp(clientIp(req)));
        repository.save(entry);
    }

    private JsonNode toJson(Object value) {
        if (value == null) return null;
        return value instanceof JsonNode node ? node : objectMapper.valueToTree(value);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static InetAddress parseIp(String ip) {
        if (ip == null || !ip.matches("[0-9a-fA-F:.]+")) return null;   // chỉ nhận literal IP, không DNS lookup
        try {
            return InetAddress.getByName(ip);
        } catch (Exception e) {
            return null;
        }
    }
}
