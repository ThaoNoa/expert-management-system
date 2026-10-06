-- =====================================================================
-- V7: Assessment, Team, Assignment, Schedule, Manday, Customer notification
--     (Module 7, 8, 16)
-- BR-16.1.1..3: Customer/Assessment thuộc sở hữu Hệ thống kế hoạch.
--   EMS chỉ giữ bản tham chiếu tối thiểu (external_ref) để lập đoàn & báo cáo.
-- =====================================================================

CREATE TABLE customers (
    customer_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_ref  VARCHAR(100) NOT NULL UNIQUE,    -- ID bên hệ thống kế hoạch
    customer_code VARCHAR(50)  NOT NULL,
    customer_name VARCHAR(500) NOT NULL,
    address       VARCHAR(500),
    location_id   UUID REFERENCES locations(location_id),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    synced_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_customers_code ON customers (customer_code);
CREATE INDEX ix_customers_name_trgm ON customers USING gin (f_unaccent(lower(customer_name)) gin_trgm_ops);

CREATE TABLE certification_programs (
    program_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_ref VARCHAR(100) UNIQUE,
    customer_id  UUID NOT NULL REFERENCES customers(customer_id),
    contract_code VARCHAR(100),
    scheme_id    UUID NOT NULL REFERENCES schemes(scheme_id),
    standard_id  UUID NOT NULL REFERENCES standards(standard_id),
    cycle_start  DATE,
    cycle_end    DATE,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN','CLOSED'))
);
CREATE INDEX ix_programs_customer ON certification_programs (customer_id);

-- Assessment Event = 1 cuộc đánh giá cụ thể (gộp Plan + Event của SRS cho gọn;
-- plan_ref giữ liên kết tới kế hoạch bên hệ thống kế hoạch - BR-13.6.4)
CREATE TABLE assessment_events (
    event_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_ref  VARCHAR(100) UNIQUE,
    event_code    VARCHAR(50)  NOT NULL UNIQUE,         -- 2026-001
    program_id    UUID NOT NULL REFERENCES certification_programs(program_id),
    plan_ref      VARCHAR(500),                         -- URL kế hoạch / báo cáo đánh giá
    report_ref    VARCHAR(500),
    event_type    VARCHAR(20) NOT NULL
                  CHECK (event_type IN ('STAGE1','STAGE2','SURVEILLANCE','RECERTIFICATION','SPECIAL','TRANSFER')),
    audit_mode    VARCHAR(20) NOT NULL DEFAULT 'ONSITE' CHECK (audit_mode IN ('ONSITE','REMOTE','HYBRID')),  -- IAF MD 4
    language      VARCHAR(10) NOT NULL DEFAULT 'vi',
    from_date     DATE NOT NULL,
    to_date       DATE NOT NULL,
    planned_mandays NUMERIC(6,2),                       -- IAF MD 5
    status        VARCHAR(20) NOT NULL DEFAULT 'REQUESTED'
                  CHECK (status IN ('REQUESTED','PLANNING','TEAM_CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_event_dates CHECK (to_date >= from_date)
);
CREATE INDEX ix_events_dates ON assessment_events (from_date, to_date);
CREATE INDEX ix_events_program ON assessment_events (program_id);

ALTER TABLE matching_runs ADD CONSTRAINT fk_matching_runs_event FOREIGN KEY (event_id) REFERENCES assessment_events(event_id);

CREATE TABLE assessment_sites (
    site_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES assessment_events(event_id),
    site_name   VARCHAR(255) NOT NULL,
    location_id UUID REFERENCES locations(location_id),
    is_central  BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE assessment_scopes (
    scope_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES assessment_events(event_id),
    standard_id UUID NOT NULL REFERENCES standards(standard_id),
    code_id     UUID REFERENCES codes(code_id),
    industry_id UUID REFERENCES industries(industry_id),
    activity_id UUID REFERENCES activities(activity_id),
    site_id     UUID REFERENCES assessment_sites(site_id),
    process     VARCHAR(255),
    description TEXT
);
CREATE INDEX ix_scopes_event ON assessment_scopes (event_id);

-- Yêu cầu năng lực của cuộc đánh giá (đầu vào Coverage Engine)
CREATE TABLE required_competencies (
    required_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id           UUID NOT NULL REFERENCES assessment_events(event_id),
    standard_id        UUID NOT NULL REFERENCES standards(standard_id),
    code_id            UUID REFERENCES codes(code_id),                    -- NULL = yêu cầu vai trò thuần (VD 1 LA)
    assessment_role_id UUID REFERENCES assessment_roles(assessment_role_id), -- NULL = bất kỳ vai trò tính coverage
    quantity           SMALLINT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    CONSTRAINT ck_required_not_empty CHECK (code_id IS NOT NULL OR assessment_role_id IS NOT NULL)
);
CREATE INDEX ix_required_event ON required_competencies (event_id);

-- ---------- Team ----------
CREATE TABLE assessment_teams (
    team_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID NOT NULL REFERENCES assessment_events(event_id),
    revision_no  INT  NOT NULL DEFAULT 1,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                 CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','NOTIFIED','ACCEPTED','OBJECTED',
                                   'COMPLETED','CANCELLED','SUPERSEDED')),
    coverage_ratio NUMERIC(5,4),                -- snapshot khi submit
    submitted_by UUID REFERENCES users(user_id),
    submitted_at TIMESTAMPTZ,
    approved_by  UUID REFERENCES users(user_id),
    approved_at  TIMESTAMPTZ,
    created_by   UUID REFERENCES users(user_id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_team_revision UNIQUE (event_id, revision_no)
);
-- Mỗi event chỉ 1 đoàn đang hiệu lực
CREATE UNIQUE INDEX ux_team_active ON assessment_teams (event_id)
    WHERE status NOT IN ('CANCELLED','SUPERSEDED');
CREATE TRIGGER teams_updated_at BEFORE UPDATE ON assessment_teams FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE team_members (
    team_member_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id            UUID NOT NULL REFERENCES assessment_teams(team_id) ON DELETE CASCADE,
    expert_id          UUID NOT NULL REFERENCES experts(expert_id),
    assessment_role_id UUID NOT NULL REFERENCES assessment_roles(assessment_role_id),
    start_date         DATE NOT NULL,
    end_date           DATE NOT NULL,
    evaluator_id       UUID REFERENCES experts(expert_id),     -- người giám sát trainee/witness
    matching_run_id    UUID REFERENCES matching_runs(matching_run_id),  -- bằng chứng eligibility lúc chọn
    override_reason    TEXT,                                    -- lý do chấp nhận WARNING
    CONSTRAINT ux_team_member UNIQUE (team_id, expert_id),
    CONSTRAINT ck_tm_dates CHECK (end_date >= start_date)
);
CREATE INDEX ix_team_members_expert ON team_members (expert_id);

-- Code assignment (chuẩn hoá thay cho cột text code_assignment) - đầu vào coverage
CREATE TABLE team_member_codes (
    team_member_id UUID NOT NULL REFERENCES team_members(team_member_id) ON DELETE CASCADE,
    standard_id    UUID NOT NULL REFERENCES standards(standard_id),
    code_id        UUID NOT NULL REFERENCES codes(code_id),
    PRIMARY KEY (team_member_id, standard_id, code_id)
);

-- Assignment Matrix (FR-7.4): site / process / activity
CREATE TABLE team_assignments (
    assignment_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_member_id UUID NOT NULL REFERENCES team_members(team_member_id) ON DELETE CASCADE,
    site_id        UUID REFERENCES assessment_sites(site_id),
    scope_id       UUID REFERENCES assessment_scopes(scope_id),
    activity_id    UUID REFERENCES activities(activity_id),
    process        VARCHAR(255),
    work_date      DATE,
    note           VARCHAR(500)
);
CREATE INDEX ix_team_assignments_member ON team_assignments (team_member_id);

-- ---------- Schedule (FR-8.2) ----------
-- BR-8.2.1: không xếp trùng lịch -> exclusion constraint cấp DB.
-- Travel buffer / khoảng cách (BR-8.2.2/3) kiểm tra ở Rule Engine (SCHEDULE group).
CREATE TABLE schedules (
    schedule_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id      UUID NOT NULL REFERENCES experts(expert_id),
    event_id       UUID REFERENCES assessment_events(event_id),
    team_member_id UUID REFERENCES team_members(team_member_id) ON DELETE CASCADE,
    schedule_type  VARCHAR(20) NOT NULL DEFAULT 'ASSESSMENT'
                   CHECK (schedule_type IN ('ASSESSMENT','WITNESS','TRAVEL','TRAINING')),
    period         TSTZRANGE NOT NULL,
    location_id    UUID REFERENCES locations(location_id),
    status         VARCHAR(20) NOT NULL DEFAULT 'TENTATIVE'
                   CHECK (status IN ('TENTATIVE','CONFIRMED','CANCELLED','DONE')),
    expert_confirmed_at TIMESTAMPTZ,            -- chuyên gia xác nhận lịch
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ex_schedule_no_overlap EXCLUDE USING gist (expert_id WITH =, period WITH &&)
        WHERE (status IN ('TENTATIVE','CONFIRMED'))
);
CREATE INDEX ix_schedules_period ON schedules USING gist (period);

-- ---------- Manday (FR-8.3) ----------
CREATE TABLE manday_records (
    manday_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id      UUID NOT NULL REFERENCES experts(expert_id),
    event_id       UUID REFERENCES assessment_events(event_id),
    team_member_id UUID REFERENCES team_members(team_member_id),
    manday_type    VARCHAR(20) NOT NULL
                   CHECK (manday_type IN ('PLANNED','ALLOCATED','ACTUAL','BILLABLE','TRAVEL','TRAINING','WITNESS')),
    work_date      DATE NOT NULL,
    quantity       NUMERIC(4,2) NOT NULL CHECK (quantity > 0 AND quantity <= 1.5),
    assessment_role_id UUID REFERENCES assessment_roles(assessment_role_id),
    note           VARCHAR(500),
    recorded_by    UUID REFERENCES users(user_id),
    recorded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked         BOOLEAN NOT NULL DEFAULT false,    -- khoá sau khi chốt KPI tháng (BR-8.3.1)
    CONSTRAINT ux_manday UNIQUE (expert_id, event_id, manday_type, work_date)
);
CREATE INDEX ix_manday_expert_date ON manday_records (expert_id, work_date);
CREATE INDEX ix_manday_date_type   ON manday_records (work_date, manday_type);

CREATE OR REPLACE FUNCTION trg_manday_locked()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.locked THEN
        RAISE EXCEPTION 'Manday % is locked (KPI period closed)', OLD.manday_id USING ERRCODE = 'check_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER manday_locked BEFORE UPDATE OR DELETE ON manday_records
    FOR EACH ROW EXECUTE FUNCTION trg_manday_locked();

-- KPI manday theo tháng (BR-13.6.3)
CREATE VIEW v_manday_monthly AS
SELECT expert_id,
       date_trunc('month', work_date)::date AS month,
       manday_type,
       SUM(quantity)                         AS mandays,
       COUNT(DISTINCT event_id)              AS events
FROM manday_records
GROUP BY expert_id, date_trunc('month', work_date), manday_type;

-- ---------- Customer notification (FR-7.5) ----------
CREATE TABLE customer_notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id         UUID NOT NULL REFERENCES assessment_teams(team_id),
    channel         VARCHAR(20) NOT NULL DEFAULT 'EMAIL' CHECK (channel IN ('EMAIL','PORTAL','LETTER')),
    sent_to         VARCHAR(500),
    sent_at         TIMESTAMPTZ,
    response_due    DATE,
    content         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','SENT','ACCEPTED','OBJECTED','NO_RESPONSE')),
    created_by      UUID REFERENCES users(user_id)
);

CREATE TABLE customer_responses (
    response_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id  UUID NOT NULL REFERENCES customer_notifications(notification_id),
    response_type    VARCHAR(20) NOT NULL CHECK (response_type IN ('ACCEPTED','OBJECTED')),
    objected_expert_id UUID REFERENCES experts(expert_id),
    content          TEXT,
    responded_at     TIMESTAMPTZ NOT NULL,
    handled_by       UUID REFERENCES users(user_id),
    handling_decision VARCHAR(20) CHECK (handling_decision IN ('CHANGE_TEAM','KEEP_WITH_JUSTIFICATION')),
    handling_note    TEXT,
    handled_at       TIMESTAMPTZ
);

-- ---------- Coverage Engine (FR-6.1, BR-COV-001..005) ----------
-- Trả về từng yêu cầu + số người đáp ứng. Một thành viên đáp ứng yêu cầu khi:
--   (1) đúng role (nếu yêu cầu chỉ định role) và role được tính coverage,
--   (2) được gán code (team_member_codes) bằng code yêu cầu, hoặc là code cha
--       khi scheme bật parent_covers_child,
--   (3) có expert_competency APPROVED còn hiệu lực trong suốt thời gian event
--       cho cùng Standard/Code(+cha)/Role.
CREATE OR REPLACE FUNCTION fn_team_coverage(p_team_id uuid)
RETURNS TABLE (required_id uuid, standard_code text, code_value text, role_code text,
               required_qty int, covered_qty int, is_covered boolean)
LANGUAGE sql STABLE AS $$
WITH t AS (
    SELECT tm.*, e.from_date, e.to_date, e.event_id
    FROM assessment_teams at
    JOIN assessment_events e ON e.event_id = at.event_id
    JOIN team_members tm     ON tm.team_id = at.team_id
    JOIN assessment_roles ar ON ar.assessment_role_id = tm.assessment_role_id AND ar.counts_for_coverage
    WHERE at.team_id = p_team_id
),
req AS (
    SELECT rc.*, s.standard_code, s.scheme_id, sc.parent_covers_child,
           c.code_value, c.path AS req_path, ar.role_code
    FROM required_competencies rc
    JOIN assessment_teams at ON at.event_id = rc.event_id AND at.team_id = p_team_id
    JOIN standards s  ON s.standard_id = rc.standard_id
    JOIN schemes  sc  ON sc.scheme_id = s.scheme_id
    LEFT JOIN codes c ON c.code_id = rc.code_id
    LEFT JOIN assessment_roles ar ON ar.assessment_role_id = rc.assessment_role_id
)
SELECT r.required_id, r.standard_code::text, r.code_value::text, r.role_code::text,
       r.quantity::int,
       COUNT(DISTINCT t.team_member_id)::int AS covered_qty,
       COUNT(DISTINCT t.team_member_id) >= r.quantity AS is_covered
FROM req r
LEFT JOIN t ON (r.assessment_role_id IS NULL OR t.assessment_role_id = r.assessment_role_id)
  AND (
        r.code_id IS NULL
        OR EXISTS (
            SELECT 1 FROM team_member_codes tmc
            JOIN codes ac ON ac.code_id = tmc.code_id
            WHERE tmc.team_member_id = t.team_member_id
              AND tmc.standard_id = r.standard_id
              AND (ac.code_id = r.code_id OR (r.parent_covers_child AND r.req_path LIKE ac.path || '%'))
        )
      )
  AND EXISTS (
        SELECT 1 FROM expert_competencies ec
        JOIN competency_definitions cd ON cd.competency_definition_id = ec.competency_definition_id
        LEFT JOIN codes dc ON dc.code_id = cd.code_id
        WHERE ec.expert_id = t.expert_id
          AND ec.status = 'APPROVED'
          AND cd.standard_id = r.standard_id
          AND cd.assessment_role_id = t.assessment_role_id
          AND ec.effective_from <= t.from_date
          AND (ec.effective_to IS NULL OR ec.effective_to >= t.to_date)
          AND (r.code_id IS NULL
               OR dc.code_id = r.code_id
               OR (r.parent_covers_child AND dc.code_id IS NOT NULL AND r.req_path LIKE dc.path || '%'))
      )
GROUP BY r.required_id, r.standard_code, r.code_value, r.role_code, r.quantity
$$;

-- Gate: không cho SUBMIT đoàn khi coverage < 100% (BR-6.1.3, BR-7.2.3, BR-COV-004)
CREATE OR REPLACE FUNCTION trg_team_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_missing text;
    v_total int;
    v_ok int;
BEGIN
    PERFORM fn_assert_transition('TEAM', OLD.status, NEW.status);
    IF OLD.status = 'DRAFT' AND NEW.status = 'SUBMITTED' THEN
        SELECT count(*),
               count(*) FILTER (WHERE is_covered),
               string_agg(COALESCE(standard_code,'') || '/' || COALESCE(code_value,'*') || '/' || COALESCE(role_code,'*'), ', ')
                   FILTER (WHERE NOT is_covered)
          INTO v_total, v_ok, v_missing
          FROM fn_team_coverage(NEW.team_id);
        IF v_total = 0 THEN
            RAISE EXCEPTION 'Event has no required competencies defined' USING ERRCODE = 'check_violation';
        END IF;
        IF v_missing IS NOT NULL THEN
            RAISE EXCEPTION 'Coverage incomplete, missing: %', v_missing USING ERRCODE = 'check_violation';
        END IF;
        NEW.coverage_ratio := v_ok::numeric / v_total;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER team_guard BEFORE UPDATE OF status ON assessment_teams
    FOR EACH ROW EXECUTE FUNCTION trg_team_guard();

-- ---------- Integration inbox (FR-16.1, 16.2) ----------
CREATE TABLE assessment_requests (
    request_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_system  VARCHAR(50) NOT NULL DEFAULT 'PLANNING',
    external_assessment_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    payload        JSONB NOT NULL,
    event_id       UUID REFERENCES assessment_events(event_id),
    status         VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
                   CHECK (status IN ('RECEIVED','PROCESSED','FAILED')),
    error_message  TEXT,
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ
);
