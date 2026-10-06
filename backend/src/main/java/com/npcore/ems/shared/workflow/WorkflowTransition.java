package com.npcore.ems.shared.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** State machine cấu hình trong DB (seed ở V12). DB trigger cũng kiểm tra lại cặp from/to. */
@Entity
@Table(name = "workflow_transitions")
@IdClass(WorkflowTransition.Key.class)
@Immutable
@Getter
public class WorkflowTransition {
    @Id
    private String workflow;
    @Id
    @Column(name = "from_status")
    private String fromStatus;
    @Id
    @Column(name = "to_status")
    private String toStatus;
    private String action;
    @Column(name = "required_permission")
    private String requiredPermission;
    @Column(name = "requires_comment")
    private boolean requiresComment;
    @Column(name = "forbid_same_actor_as")
    private String forbidSameActorAs;

    public record Key(String workflow, String fromStatus, String toStatus) implements Serializable {
        public Key() { this(null, null, null); }
    }
}
