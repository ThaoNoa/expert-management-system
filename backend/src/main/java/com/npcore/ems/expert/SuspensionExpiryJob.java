package com.npcore.ems.expert;

import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.workflow.ApprovalHistory;
import com.npcore.ems.shared.workflow.ApprovalHistoryRepository;
import com.npcore.ems.shared.workflow.WorkflowService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yêu cầu 9: GĐCN dừng đánh giá chuyên gia trong một khoảng thời gian. Hết thời hạn (sau ngày suspended_until)
 * hệ thống tự mở lại: SUSPENDED → ACTIVE, ghi lịch sử phê duyệt (decision SYSTEM, người dừng là actor) và audit log.
 * Chạy lúc khởi động và 00:05 mỗi ngày.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspensionExpiryJob {

    private final ExpertRepository experts;
    private final ApprovalHistoryRepository history;
    private final WorkflowService workflow;
    private final AuditService audit;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public int run() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Expert e : experts.findByStatusAndSuspendedUntilBeforeAndDeletedAtIsNull("SUSPENDED", today)) {
            LocalDate until = e.getSuspendedUntil();
            String note = "Tự động mở lại: hết thời hạn dừng đánh giá (đến hết " + until.format(ExpertService.DMY) + ")";
            ApprovalHistory h = new ApprovalHistory();
            h.setObjectType("EXPERT");
            h.setObjectId(e.getId());
            h.setAction("REINSTATE");
            h.setFromStatus("SUSPENDED");
            h.setToStatus("ACTIVE");
            h.setActorId(workflow.history("EXPERT", e.getId()).stream()
                    .filter(x -> "SUSPEND".equals(x.getAction())).findFirst()
                    .map(ApprovalHistory::getActorId).orElse(e.getCreatedBy()));
            h.setDecision("SYSTEM");
            h.setComment(note);
            h.setCreatedAt(OffsetDateTime.now());
            history.save(h);
            e.setStatus("ACTIVE");
            e.setStatusReason(note);
            e.setSuspendedUntil(null);
            experts.flush();
            audit.record("AUTO_REINSTATE", "EXPERT", e.getId(), "SUSPENDED", "ACTIVE", note);
            count++;
        }
        if (count > 0) log.info("Tự mở lại {} chuyên gia hết thời hạn dừng", count);
        return count;
    }
}
