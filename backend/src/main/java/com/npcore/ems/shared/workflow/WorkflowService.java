package com.npcore.ems.shared.workflow;

import com.npcore.ems.shared.security.CurrentUser;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.web.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cơ chế chuyển trạng thái dùng chung cho Expert, Document, Competency, Restriction, Team...
 * Kiểm tra: cặp trạng thái hợp lệ, quyền, bắt buộc comment, tách biệt người làm / người duyệt (SoD);
 * sau đó ghi approval_history. Caller tự cập nhật trạng thái của entity bằng {@link Result#toStatus()}.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowTransitionRepository transitions;
    private final ApprovalHistoryRepository history;

    public record Result(String fromStatus, String toStatus, String action) {}

    /**
     * @param actors  vai trò cần tách biệt → user id, VD {"UPLOADER": uploaderId, "SUBMITTER": submitterId}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result apply(String workflow, String objectType, UUID objectId, String currentStatus, String action,
                        String comment, Map<String, UUID> actors) {
        WorkflowTransition t = transitions.findByWorkflowAndFromStatusAndAction(workflow, currentStatus, action)
                .orElseThrow(() -> ApiException.illegalTransition(
                        "Không thể thực hiện " + action + " khi trạng thái là " + currentStatus));
        CurrentUser user = SecurityUtils.currentUser();
        if (t.getRequiredPermission() == null) {
            throw ApiException.forbidden("Thao tác " + action + " chỉ do hệ thống thực hiện");
        }
        if (!user.has(t.getRequiredPermission())) {
            throw ApiException.forbidden("Thiếu quyền " + t.getRequiredPermission());
        }
        if (t.isRequiresComment() && (comment == null || comment.isBlank())) {
            throw ApiException.badRequest("Thao tác " + action + " bắt buộc nhập lý do");
        }
        if (t.getForbidSameActorAs() != null && actors != null
                && user.id().equals(actors.get(t.getForbidSameActorAs()))) {
            throw ApiException.businessRule("Người thực hiện " + action + " không được trùng với "
                    + t.getForbidSameActorAs().toLowerCase());
        }
        ApprovalHistory h = new ApprovalHistory();
        h.setObjectType(objectType);
        h.setObjectId(objectId);
        h.setAction(action);
        h.setFromStatus(currentStatus);
        h.setToStatus(t.getToStatus());
        h.setActorId(user.id());
        h.setDecision(decisionOf(action));
        h.setComment(comment);
        h.setCreatedAt(OffsetDateTime.now());
        history.save(h);
        return new Result(currentStatus, t.getToStatus(), action);
    }

    /** Các action người dùng hiện tại được phép làm từ trạng thái này (FE dùng để hiện nút). */
    @Transactional(readOnly = true)
    public List<String> availableActions(String workflow, String currentStatus) {
        CurrentUser user = SecurityUtils.currentUser();
        return transitions.findByWorkflowAndFromStatus(workflow, currentStatus).stream()
                .filter(t -> t.getRequiredPermission() != null && user.has(t.getRequiredPermission()))
                .map(WorkflowTransition::getAction)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> history(String objectType, UUID objectId) {
        return history.findByObjectTypeAndObjectIdOrderByIdDesc(objectType, objectId);
    }

    private static String decisionOf(String action) {
        return switch (action) {
            case "APPROVE", "VERIFY", "ACTIVATE", "REACTIVATE", "CONFIRM" -> "APPROVED";
            case "RETURN" -> "RETURNED";
            case "REJECT" -> "REJECTED";
            case "SUBMIT" -> "SUBMITTED";
            case "REVOKE" -> "REVOKED";
            case "SUSPEND", "DEACTIVATE" -> "SUSPENDED";
            case "RELEASE", "REINSTATE" -> "RELEASED";
            default -> null;
        };
    }
}
