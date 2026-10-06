-- =====================================================================
-- V6: Competency, Workflow, Annual Review, Rule Engine
--     (Module 4, 5, 11, 14.5, 17.2)
-- Quyết định thiết kế:
--   * Bỏ bảng competency_approvals riêng -> dùng approval_history chung (V8).
--   * Bỏ competency_restrictions riêng -> dùng restrictions chung có scope (V8).
--   * State machine lưu trong workflow_transitions (cấu hình, không hard-code),
--     được kiểm tra cả ở service lẫn trigger DB (defense in depth).
--   * Dữ liệu đã APPROVED không sửa trực tiếp -> tạo revision mới
--     (BR-VER-005/006, BR-4.2.2).
-- =====================================================================

-- ---------- SOP (nguồn của rule) ----------
CREATE TABLE sops (
    sop_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sop_code      VARCHAR(50)  NOT NULL,
    title         VARCHAR(500) NOT NULL,
    revision      VARCHAR(20)  NOT NULL,
    effective_from DATE NOT NULL,
    effective_to   DATE,
    document_id   UUID REFERENCES documents(document_id),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT','ACTIVE','SUPERSEDED')),
    CONSTRAINT ux_sop UNIQUE (sop_code, revision)
);

-- ---------- Workflow state machine (cấu hình) ----------
CREATE TABLE workflow_transitions (
    workflow            VARCHAR(50) NOT NULL,   -- COMPETENCY, EXPERT, RESTRICTION, TEAM, WITNESS, ANNUAL_REVIEW, DOCUMENT, CUSTOMER_NOTIFICATION
    from_status         VARCHAR(30) NOT NULL,
    to_status           VARCHAR(30) NOT NULL,
    action              VARCHAR(50) NOT NULL,   -- SUBMIT, START_REVIEW, RETURN, APPROVE, ...
    required_permission VARCHAR(100),           -- NULL = hệ thống (batch job)
    requires_comment    BOOLEAN NOT NULL DEFAULT false,
    forbid_same_actor_as VARCHAR(30),           -- SoD: 'SUBMITTER' => người duyệt != người nộp
    PRIMARY KEY (workflow, from_status, to_status)
);

CREATE OR REPLACE FUNCTION fn_assert_transition(p_workflow text, p_from text, p_to text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF p_from IS DISTINCT FROM p_to AND NOT EXISTS (
        SELECT 1 FROM workflow_transitions
        WHERE workflow = p_workflow AND from_status = p_from AND to_status = p_to)
    THEN
        RAISE EXCEPTION 'Illegal % transition: % -> %', p_workflow, p_from, p_to
            USING ERRCODE = 'check_violation';
    END IF;
END $$;

-- ---------- Competency definition (catalog tổ hợp Standard/Code/Role + tiêu chí) ----------
CREATE TABLE competency_definitions (
    competency_definition_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme_id          UUID NOT NULL REFERENCES schemes(scheme_id),
    standard_id        UUID NOT NULL REFERENCES standards(standard_id),
    code_id            UUID REFERENCES codes(code_id),          -- NULL: năng lực theo tiêu chuẩn, không theo code (VD LA)
    assessment_role_id UUID NOT NULL REFERENCES assessment_roles(assessment_role_id),
    -- Tiêu chí đầu vào (học vấn, số năm KN, đào tạo, số cuộc đánh giá...) - dùng cho checklist thẩm tra
    criteria           JSONB NOT NULL DEFAULT '{}'::jsonb,
    default_validity_months SMALLINT,                           -- VD 36 tháng
    version            VARCHAR(20) NOT NULL DEFAULT '1',
    effective_from     DATE NOT NULL,
    effective_to       DATE,
    sop_id             UUID REFERENCES sops(sop_id),
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
);
CREATE UNIQUE INDEX ux_competency_def
    ON competency_definitions (standard_id, COALESCE(code_id, '00000000-0000-0000-0000-000000000000'::uuid), assessment_role_id, version);

-- ---------- Expert competency (có revision) ----------
CREATE TABLE expert_competencies (
    expert_competency_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id                UUID NOT NULL REFERENCES experts(expert_id),
    competency_definition_id UUID NOT NULL REFERENCES competency_definitions(competency_definition_id),
    standard_version_id      UUID REFERENCES standard_versions(standard_version_id),  -- năng lực theo phiên bản TC
    competency_level         VARCHAR(20) NOT NULL DEFAULT 'QUALIFIED'
                             CHECK (competency_level IN ('IN_TRAINING','QUALIFIED','SENIOR')),
    status                   VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                             CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','NEED_REVISION','APPROVED',
                                               'REVIEW_REQUIRED','SUSPENDED','EXPIRED','REVOKED','SUPERSEDED','REJECTED')),
    revision_no              INT  NOT NULL DEFAULT 1,
    supersedes_id            UUID REFERENCES expert_competencies(expert_competency_id),
    effective_from           DATE,
    effective_to             DATE,
    first_approved_date      DATE,       -- BR-4.2.4/4.2.5: ngày phê duyệt lần đầu theo từng Code (giữ qua các revision)
    approved_by              UUID REFERENCES users(user_id),
    approved_at              TIMESTAMPTZ,
    submitted_by             UUID REFERENCES users(user_id),
    submitted_at             TIMESTAMPTZ,
    notes                    TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               UUID REFERENCES users(user_id),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ec_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_ec_approved CHECK (status NOT IN ('APPROVED') OR (approved_by IS NOT NULL AND effective_from IS NOT NULL))
);
-- Mỗi Expert x Definition: tối đa 1 bản đang hiệu lực + 1 revision đang xử lý.
-- Khi revision mới được duyệt: service chuyển bản cũ -> SUPERSEDED TRƯỚC, rồi bản mới -> APPROVED (cùng transaction).
CREATE UNIQUE INDEX ux_expert_competency_effective
    ON expert_competencies (expert_id, competency_definition_id)
    WHERE status IN ('APPROVED','REVIEW_REQUIRED','SUSPENDED');
CREATE UNIQUE INDEX ux_expert_competency_pending
    ON expert_competencies (expert_id, competency_definition_id)
    WHERE status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','NEED_REVISION');
-- Index phục vụ Matching Engine
CREATE INDEX ix_ec_matching ON expert_competencies (competency_definition_id, status, effective_to) INCLUDE (expert_id);
CREATE INDEX ix_ec_expert   ON expert_competencies (expert_id, status);
CREATE INDEX ix_ec_expiry   ON expert_competencies (effective_to) WHERE status = 'APPROVED';
CREATE TRIGGER ec_updated_at BEFORE UPDATE ON expert_competencies FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- Bảo vệ dữ liệu đã duyệt + ép state machine ở tầng DB
CREATE OR REPLACE FUNCTION trg_expert_competency_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM fn_assert_transition('COMPETENCY', OLD.status, NEW.status);

    IF OLD.status IN ('APPROVED','REVIEW_REQUIRED','SUSPENDED','EXPIRED','REVOKED','SUPERSEDED') THEN
        IF (NEW.expert_id, NEW.competency_definition_id, NEW.standard_version_id, NEW.competency_level,
            NEW.effective_from, NEW.effective_to, NEW.approved_by, NEW.approved_at, NEW.revision_no, NEW.first_approved_date)
           IS DISTINCT FROM
           (OLD.expert_id, OLD.competency_definition_id, OLD.standard_version_id, OLD.competency_level,
            OLD.effective_from, OLD.effective_to, OLD.approved_by, OLD.approved_at, OLD.revision_no, OLD.first_approved_date)
        THEN
            RAISE EXCEPTION 'Approved competency % is immutable; create a new revision', OLD.expert_competency_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER ec_guard BEFORE UPDATE ON expert_competencies
    FOR EACH ROW EXECUTE FUNCTION trg_expert_competency_guard();

-- Cấm xoá competency (chỉ REVOKE)
CREATE TRIGGER ec_no_delete BEFORE DELETE ON expert_competencies
    FOR EACH ROW WHEN (OLD.status <> 'DRAFT') EXECUTE FUNCTION trg_forbid_modify();

CREATE TABLE competency_evidence (
    evidence_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_competency_id UUID NOT NULL REFERENCES expert_competencies(expert_competency_id),
    document_id          UUID REFERENCES documents(document_id),
    evidence_type        VARCHAR(30) NOT NULL
                         CHECK (evidence_type IN ('EDUCATION','EXPERIENCE','TRAINING','CERTIFICATE','AUDIT_LOG',
                                                  'COMPETENCE_TEST','WITNESS','INTERVIEW','OTHER')),
    source_object_type   VARCHAR(30),       -- link tới education/experience/training... thay vì upload lại
    source_object_id     UUID,
    description          TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_competency_evidence ON competency_evidence (expert_competency_id);

-- Checklist thẩm tra theo criteria của definition
CREATE TABLE competency_assessments (
    assessment_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_competency_id UUID NOT NULL REFERENCES expert_competencies(expert_competency_id),
    reviewer_id          UUID NOT NULL REFERENCES users(user_id),
    checklist            JSONB NOT NULL,     -- [{criterion, required, actual, met, note}]
    recommendation       VARCHAR(20) NOT NULL CHECK (recommendation IN ('APPROVE','RETURN','REJECT')),
    comment              TEXT,
    reviewed_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Annual Review (Module 11) ----------
CREATE TABLE annual_reviews (
    annual_review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id        UUID NOT NULL REFERENCES experts(expert_id),
    review_year      SMALLINT NOT NULL,
    due_date         DATE NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                     CHECK (status IN ('OPEN','SELF_UPDATED','UNDER_REVIEW','COMPLETED','OVERDUE','CANCELLED')),
    checklist        JSONB NOT NULL DEFAULT '{}'::jsonb,  -- profileUpdated, trainingCompleted, experienceSufficient, competencyValid, witnessCompleted, performanceAcceptable, documentsValid
    self_updated_at  TIMESTAMPTZ,
    reviewer_id      UUID REFERENCES users(user_id),
    overall_result   VARCHAR(20) CHECK (overall_result IN ('RENEW','RESTRICT','SUSPEND')),
    completed_at     TIMESTAMPTZ,
    notes            TEXT,
    row_version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_annual_review UNIQUE (expert_id, review_year)
);
CREATE INDEX ix_annual_reviews_due ON annual_reviews (due_date) WHERE status IN ('OPEN','SELF_UPDATED','UNDER_REVIEW');

CREATE TABLE annual_review_items (
    item_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    annual_review_id     UUID NOT NULL REFERENCES annual_reviews(annual_review_id) ON DELETE CASCADE,
    expert_competency_id UUID NOT NULL REFERENCES expert_competencies(expert_competency_id),
    result               VARCHAR(20) CHECK (result IN ('RENEW','RESTRICT','SUSPEND')),
    new_effective_to     DATE,
    comment              TEXT,
    CONSTRAINT ux_annual_review_item UNIQUE (annual_review_id, expert_competency_id)
);

-- ---------- Rule Engine (FR-5.3, FR-14.5, BR-5.3.1) ----------
CREATE TABLE rule_definitions (
    rule_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code       VARCHAR(50)  NOT NULL,          -- BR-MATCH-001, BR-WARN-003...
    rule_name       VARCHAR(255) NOT NULL,
    description     TEXT,
    rule_group      VARCHAR(30)  NOT NULL CHECK (rule_group IN ('MATCHING','COVERAGE','SCHEDULE','IMPARTIALITY','ALERT')),
    severity        VARCHAR(20)  NOT NULL CHECK (severity IN ('BLOCKER','WARNING','INFORMATION')),
    priority        INT          NOT NULL DEFAULT 100,
    scheme_id       UUID REFERENCES schemes(scheme_id),   -- NULL = áp dụng mọi scheme
    -- BUILTIN: evaluator Java có sẵn, tham số qua parameters; SPEL/MVEL: biểu thức cấu hình
    expression_lang VARCHAR(20)  NOT NULL DEFAULT 'BUILTIN' CHECK (expression_lang IN ('BUILTIN','SPEL','MVEL','DRL')),
    evaluator_key   VARCHAR(100),                        -- bean name khi BUILTIN
    expression      TEXT,
    parameters      JSONB NOT NULL DEFAULT '{}'::jsonb,  -- VD {"warnDays":60}
    message_template VARCHAR(1000) NOT NULL,             -- "Competency expired: {effectiveTo}"
    version         VARCHAR(20)  NOT NULL,               -- 2026.01
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    sop_id          UUID REFERENCES sops(sop_id),
    sop_clause      VARCHAR(50),
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID REFERENCES users(user_id),
    CONSTRAINT ux_rule_version UNIQUE (rule_code, version),
    CONSTRAINT ck_rule_expr CHECK ((expression_lang = 'BUILTIN' AND evaluator_key IS NOT NULL)
                                   OR (expression_lang <> 'BUILTIN' AND expression IS NOT NULL))
);
CREATE INDEX ix_rule_active ON rule_definitions (rule_group, effective_from) WHERE enabled;

-- Snapshot mỗi lần chạy matching -> truy vết "vì sao chọn/không chọn" (BR-5.2.4, NFR-T-01)
CREATE TABLE matching_runs (
    matching_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID,                         -- FK thêm ở V7
    requested_by    UUID REFERENCES users(user_id),
    source          VARCHAR(20) NOT NULL DEFAULT 'UI' CHECK (source IN ('UI','API','BATCH')),
    request         JSONB NOT NULL,
    rule_versions   JSONB NOT NULL,               -- {"BR-MATCH-001":"2026.01",...}
    candidate_count INT NOT NULL,
    eligible_count  INT NOT NULL,
    duration_ms     INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE matching_results (
    matching_run_id UUID NOT NULL REFERENCES matching_runs(matching_run_id),
    expert_id       UUID NOT NULL REFERENCES experts(expert_id),
    eligible        BOOLEAN NOT NULL,
    score           NUMERIC(8,3),
    covered_codes   VARCHAR(50)[] NOT NULL DEFAULT '{}',
    covered_roles   VARCHAR(20)[] NOT NULL DEFAULT '{}',
    rule_results    JSONB NOT NULL,              -- [{ruleCode, severity, passed, message}]
    PRIMARY KEY (matching_run_id, expert_id)
);
CREATE TRIGGER matching_runs_immutable    BEFORE UPDATE OR DELETE ON matching_runs    FOR EACH ROW EXECUTE FUNCTION trg_forbid_modify();
CREATE TRIGGER matching_results_immutable BEFORE UPDATE OR DELETE ON matching_results FOR EACH ROW EXECUTE FUNCTION trg_forbid_modify();
