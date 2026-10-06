-- =====================================================================
-- V9: Witness & Monitoring, Performance, Corrective Action (Module 10)
-- =====================================================================

-- Kế hoạch witness theo chu kỳ 3 năm (FR-10.1)
CREATE TABLE witness_plans (
    witness_plan_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id       UUID NOT NULL REFERENCES experts(expert_id),
    standard_id     UUID NOT NULL REFERENCES standards(standard_id),
    code_id         UUID REFERENCES codes(code_id),
    assessment_role_id UUID REFERENCES assessment_roles(assessment_role_id),
    cycle_start     DATE NOT NULL,
    cycle_end       DATE NOT NULL,
    planned_date    DATE NOT NULL,          -- hạn witness (dùng cảnh báo BR-WARN-005)
    witnesser_id    UUID REFERENCES experts(expert_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
                    CHECK (status IN ('PLANNED','SCHEDULED','COMPLETED','OVERDUE','CANCELLED')),
    notes           TEXT,
    created_by      UUID REFERENCES users(user_id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wp_cycle CHECK (cycle_end > cycle_start AND planned_date BETWEEN cycle_start AND cycle_end),
    CONSTRAINT ck_wp_not_self CHECK (witnesser_id IS NULL OR witnesser_id <> expert_id)
);
CREATE INDEX ix_witness_plans_due ON witness_plans (planned_date) WHERE status IN ('PLANNED','SCHEDULED');
CREATE INDEX ix_witness_plans_expert ON witness_plans (expert_id);

-- Witness thực tế, gắn với một cuộc đánh giá (FR-10.2/10.3)
CREATE TABLE witness_events (
    witness_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    witness_plan_id  UUID NOT NULL REFERENCES witness_plans(witness_plan_id),
    event_id         UUID REFERENCES assessment_events(event_id),
    witnesser_id     UUID NOT NULL REFERENCES experts(expert_id),
    scheduled_date   DATE NOT NULL,
    actual_date      DATE,
    location_id      UUID REFERENCES locations(location_id),
    mode             VARCHAR(20) NOT NULL DEFAULT 'ONSITE' CHECK (mode IN ('ONSITE','REMOTE')),
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                     CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED','OVERDUE')),
    row_version      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_witness_events_plan ON witness_events (witness_plan_id);

-- FR-10.4: PASS/CONDITIONAL/FAIL; FAIL -> competency REVIEW_REQUIRED (xử lý ở service + approval_history)
CREATE TABLE witness_evaluations (
    evaluation_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    witness_event_id UUID NOT NULL UNIQUE REFERENCES witness_events(witness_event_id),
    result           VARCHAR(20) NOT NULL CHECK (result IN ('PASS','CONDITIONAL','FAIL')),
    criteria_scores  JSONB NOT NULL DEFAULT '[]'::jsonb,   -- [{criterion, score, comment}] theo ISO 19011 §7
    evaluation_date  DATE NOT NULL,
    evaluator_id     UUID NOT NULL REFERENCES users(user_id),
    notes            TEXT,
    report_document_id UUID REFERENCES documents(document_id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE corrective_actions (
    action_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type    VARCHAR(30) NOT NULL CHECK (source_type IN ('WITNESS','ANNUAL_REVIEW','PERFORMANCE','COMPLAINT')),
    source_id      UUID NOT NULL,
    expert_id      UUID NOT NULL REFERENCES experts(expert_id),
    action_description TEXT NOT NULL,
    due_date       DATE NOT NULL,
    completed_date DATE,
    verification_note TEXT,
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN','IN_PROGRESS','DONE','VERIFIED','OVERDUE','CANCELLED')),
    responsible_id UUID REFERENCES users(user_id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ca_expert ON corrective_actions (expert_id, status);

-- Đánh giá hiệu quả (feedback KH, review báo cáo, ...) - Phase 4
CREATE TABLE performance_evaluations (
    evaluation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id     UUID NOT NULL REFERENCES experts(expert_id),
    event_id      UUID REFERENCES assessment_events(event_id),
    source        VARCHAR(30) NOT NULL CHECK (source IN ('CUSTOMER_FEEDBACK','REPORT_REVIEW','TEAM_LEADER','PERIODIC')),
    period_from   DATE,
    period_to     DATE,
    score         NUMERIC(5,2) CHECK (score BETWEEN 0 AND 100),
    details       JSONB,
    evaluator_id  UUID REFERENCES users(user_id),
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_perf_expert ON performance_evaluations (expert_id, created_at);
