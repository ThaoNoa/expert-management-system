package com.npcore.ems.shared.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByObjectTypeAndObjectIdOrderByIdDesc(String objectType, UUID objectId);
}
