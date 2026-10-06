-- =====================================================================
-- V8: Restriction, Impartiality, Conflict of Interest, Approval History, Audit Log
--     (Module 4.5, 9, 14)
-- =====================================================================

-- ---------- Restriction / Stop (FR-4.5, BR-RES-001..004) ----------
-- Một bảng duy nhất cho mọi cấp: toàn bộ chuyên gia hoặc theo scope.
-- Cột scope NULL = "mọi giá trị". VD: chỉ dừng ISO 22000 / Code K -> standard_id + code_id.
CREATE TABLE restrictions (
    restriction_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id          UUID NOT NULL REFERENCES experts(expert_id),
    restriction_type   VARCHAR(30) NOT NULL
                       CHECK (restriction_type IN ('SUSPEND',             -- dừng đánh giá trong scope
                                                   'ROLE_LIMIT',          -- chỉ được làm role thấp hơn
                                                   'SUPERVISION_REQUIRED',-- phải có người giám sát
                                                   'CUSTOMER_BAN')),      -- cấm với 1 khách hàng
    scheme_id          UUID REFERENCES schemes(scheme_id),
    standard_id        UUID REFERENCES standards(standard_id),
    code_id            UUID REFERENCES codes(code_id),
    assessment_role_id UUID REFERENCES assessment_roles(assessment_role_id),
    customer_id        UUID REFERENCES customers(customer_id),
    expert_competency_id UUID REFERENCES expert_competencies(expert_competency_id),
    reason             TEXT NOT NULL,
    source             VARCHAR(30) NOT NULL DEFAULT 'MANUAL'
                       CHECK (source IN ('MANUAL','WITNESS_FAIL','ANNUAL_REVIEW','COMPLAINT','CONFLICT')),
    from_date          DATE NOT NULL,
    to_date            DATE,                           -- NULL = vô thời hạn tới khi release
    release_condition  TEXT,
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','RELEASED','EXPIRED','CANCELLED')),
    created_by         UUID NOT NULL REFERENCES users(user_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by        UUID REFERENCES users(user_id),
    approved_at        TIMESTAMPTZ,
    released_by        UUID REFERENCES users(user_id),
    released_date      DATE,
    release_note       TEXT,
    row_version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_restr_dates CHECK (to_date IS NULL OR to_date >= from_date),
    CONSTRAINT ck_restr_sod   CHECK (approved_by IS NULL OR approved_by <> created_by),   -- maker-checker
    CONSTRAINT ck_restr_customer CHECK (restriction_type <> 'CUSTOMER_BAN' OR customer_id IS NOT NULL)
);
CREATE INDEX ix_restrictions_active ON restrictions (expert_id, from_date, to_date) WHERE status = 'ACTIVE';

CREATE OR REPLACE FUNCTION trg_restriction_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM fn_assert_transition('RESTRICTION', OLD.status, NEW.status);
    RETURN NEW;
END $$;
CREATE TRIGGER restriction_guard BEFORE UPDATE OF status ON restrictions
    FOR EACH ROW EXECUTE FUNCTION trg_restriction_guard();

-- ---------- Impartiality (FR-9.1, BR-IMP-001..003) ----------
CREATE TABLE impartiality_declarations (
    declaration_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id         UUID NOT NULL REFERENCES experts(expert_id),
    declaration_scope VARCHAR(20) NOT NULL CHECK (declaration_scope IN ('ANNUAL','EVENT')),
    event_id          UUID REFERENCES assessment_events(event_id),
    customer_id       UUID REFERENCES customers(customer_id),
    valid_year        SMALLINT,
    no_interest       BOOLEAN NOT NULL,
    no_consulting     BOOLEAN NOT NULL,          -- không tư vấn cho KH (17021-1: 2 năm)
    no_employment     BOOLEAN NOT NULL,
    no_other_conflict BOOLEAN NOT NULL,
    disclosure        TEXT,                      -- mô tả nếu có câu trả lời "false"
    declared_by       UUID NOT NULL REFERENCES users(user_id),
    declared_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    signature_document_id UUID REFERENCES documents(document_id),
    status            VARCHAR(20) NOT NULL DEFAULT 'DECLARED'
                      CHECK (status IN ('DECLARED','CONFLICT_REPORTED','REVIEWED','SUPERSEDED')),
    CONSTRAINT ck_decl_scope CHECK ((declaration_scope = 'EVENT' AND event_id IS NOT NULL)
                                 OR (declaration_scope = 'ANNUAL' AND valid_year IS NOT NULL))
);
CREATE UNIQUE INDEX ux_decl_event ON impartiality_declarations (expert_id, event_id)
    WHERE declaration_scope = 'EVENT' AND status <> 'SUPERSEDED';
CREATE UNIQUE INDEX ux_decl_annual ON impartiality_declarations (expert_id, valid_year)
    WHERE declaration_scope = 'ANNUAL' AND status <> 'SUPERSEDED';

-- ---------- Conflict of interest (FR-9.2, BR-9.2.1: BLOCK matching) ----------
CREATE TABLE conflict_of_interest_records (
    conflict_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id      UUID NOT NULL REFERENCES experts(expert_id),
    customer_id    UUID NOT NULL REFERENCES customers(customer_id),
    declaration_id UUID REFERENCES impartiality_declarations(declaration_id),
    conflict_type  VARCHAR(30) NOT NULL
                   CHECK (conflict_type IN ('FINANCIAL','CONSULTING','EMPLOYMENT','FAMILY','PERSONAL','OTHER')),
    description    TEXT NOT NULL,
    valid_from     DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to       DATE,                         -- VD tư vấn: hết xung đột sau 2 năm
    -- PENDING_REVIEW cũng BLOCK (an toàn mặc định), CLEARED = xác định không xung đột
    result         VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
                   CHECK (result IN ('PENDING_REVIEW','CONFIRMED','CLEARED')),
    reviewed_by    UUID REFERENCES users(user_id),
    reviewed_at    TIMESTAMPTZ,
    decision       TEXT,
    created_by     UUID REFERENCES users(user_id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_coi_lookup ON conflict_of_interest_records (expert_id, customer_id) WHERE result <> 'CLEARED';

-- ---------- Approval History (FR-14.2, BR-4.2.1/4.2.3) - append-only ----------
CREATE TABLE approval_history (
    history_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_type  VARCHAR(30) NOT NULL,         -- COMPETENCY, EXPERT, TEAM, RESTRICTION, WITNESS, ANNUAL_REVIEW, DOCUMENT
    object_id    UUID NOT NULL,
    action       VARCHAR(50) NOT NULL,
    from_status  VARCHAR(30),
    to_status    VARCHAR(30) NOT NULL,
    actor_id     UUID NOT NULL REFERENCES users(user_id),
    decision     VARCHAR(20) CHECK (decision IN ('APPROVED','RETURNED','REJECTED','SUBMITTED','REVOKED','SUSPENDED','RELEASED','SYSTEM')),
    comment      TEXT,
    evidence_document_id UUID REFERENCES documents(document_id),
    snapshot     JSONB,                         -- trạng thái đối tượng tại thời điểm quyết định
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_approval_history_object ON approval_history (object_type, object_id, created_at);
CREATE INDEX ix_approval_history_actor  ON approval_history (actor_id, created_at);
CREATE TRIGGER approval_history_immutable BEFORE UPDATE OR DELETE ON approval_history
    FOR EACH ROW EXECUTE FUNCTION trg_forbid_modify();
CREATE TRIGGER approval_history_no_truncate BEFORE TRUNCATE ON approval_history
    FOR EACH STATEMENT EXECUTE FUNCTION trg_forbid_modify();

-- ---------- Audit Log (FR-14.1, BR-AUD-001..004) ----------
-- Append-only + hash chain (phát hiện chỉnh sửa trái phép ở tầng storage).
CREATE TABLE audit_logs (
    log_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id     UUID REFERENCES users(user_id),      -- NULL = system job
    username    VARCHAR(100),
    action      VARCHAR(50) NOT NULL,                -- CREATE, UPDATE, DELETE, LOGIN, EXPORT, VIEW_SENSITIVE...
    object_type VARCHAR(50) NOT NULL,
    object_id   VARCHAR(100),
    from_value  JSONB,
    to_value    JSONB,
    reason      TEXT,
    ip_address  INET,
    user_agent  VARCHAR(500),
    correlation_id VARCHAR(64),                      -- trace id của request
    prev_hash   CHAR(64),
    row_hash    CHAR(64) NOT NULL
);
CREATE INDEX ix_audit_object ON audit_logs (object_type, object_id, occurred_at);
CREATE INDEX ix_audit_user   ON audit_logs (user_id, occurred_at);
CREATE INDEX ix_audit_time   ON audit_logs USING brin (occurred_at);

CREATE OR REPLACE FUNCTION trg_audit_hash_chain()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('audit_logs_chain'));
    SELECT row_hash INTO NEW.prev_hash FROM audit_logs ORDER BY log_id DESC LIMIT 1;
    NEW.row_hash := encode(sha256(convert_to(
        COALESCE(NEW.prev_hash,'') || '|' || NEW.occurred_at::text || '|' || COALESCE(NEW.user_id::text,'') || '|' ||
        NEW.action || '|' || NEW.object_type || '|' || COALESCE(NEW.object_id,'') || '|' ||
        COALESCE(NEW.from_value::text,'') || '|' || COALESCE(NEW.to_value::text,'') || '|' || COALESCE(NEW.reason,''),
        'UTF8')), 'hex');
    RETURN NEW;
END $$;
CREATE TRIGGER audit_hash_chain BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION trg_audit_hash_chain();
CREATE TRIGGER audit_immutable  BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION trg_forbid_modify();
CREATE TRIGGER audit_no_truncate BEFORE TRUNCATE ON audit_logs FOR EACH STATEMENT EXECUTE FUNCTION trg_forbid_modify();

-- Kiểm tra toàn vẹn chuỗi hash (chạy định kỳ bởi ems-batch)
CREATE OR REPLACE FUNCTION fn_verify_audit_chain()
RETURNS TABLE (broken_log_id bigint) LANGUAGE sql STABLE AS $$
    SELECT log_id FROM (
        SELECT log_id, prev_hash, lag(row_hash) OVER (ORDER BY log_id) AS expected_prev
        FROM audit_logs
    ) x
    WHERE x.prev_hash IS DISTINCT FROM x.expected_prev
$$;
