package com.npcore.ems.shared.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, WorkflowTransition.Key> {
    Optional<WorkflowTransition> findByWorkflowAndFromStatusAndAction(String workflow, String fromStatus, String action);

    List<WorkflowTransition> findByWorkflowAndFromStatus(String workflow, String fromStatus);
}
