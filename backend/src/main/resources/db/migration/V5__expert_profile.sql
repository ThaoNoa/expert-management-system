-- =====================================================================
-- V5: Expert Profile (Module 2 - FR-2.1 .. FR-2.8)
-- =====================================================================

-- Quy tắc sinh mã chuyên gia (BR-2.1.2, BR-DATA-002) - cấu hình, không hard-code
CREATE TABLE expert_code_sequences (
    employment_type VARCHAR(20) PRIMARY KEY CHECK (employment_type IN ('FULLTIME','PARTTIME')),
    prefix          VARCHAR(10) NOT NULL,        -- FT- / PT-
    pad_length      SMALLINT    NOT NULL DEFAULT 4,
    next_value      BIGINT      NOT NULL DEFAULT 1
);

CREATE TABLE experts (
    expert_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_code      VARCHAR(30)  NOT NULL,
    user_id          UUID UNIQUE REFERENCES users(user_id),   -- tài khoản đăng nhập của chuyên gia
    full_name        VARCHAR(255) NOT NULL,
    date_of_birth    DATE,
    gender           VARCHAR(10) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    id_number        VARCHAR(20),                 -- CCCD: nên mã hoá ở tầng app (NFR-S-07)
    address          VARCHAR(500),
    phone            VARCHAR(30),
    email            VARCHAR(255),
    home_location_id UUID REFERENCES locations(location_id),  -- dùng tính quãng đường
    expert_type      VARCHAR(20) NOT NULL CHECK (expert_type IN ('AUDITOR','TECHNICAL_EXPERT','BOTH')), -- CGĐG / CGKT
    employment_type  VARCHAR(20) NOT NULL CHECK (employment_type IN ('FULLTIME','PARTTIME')),
    department_id    UUID REFERENCES departments(department_id),
    position         VARCHAR(255),
    joined_date      DATE,
    -- BR-4.3.1: Expert status độc lập Competency status
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','INACTIVE')),
    status_reason    TEXT,
    max_mandays_per_month NUMERIC(4,1),           -- ngưỡng workload (BR-WARN-004), NULL = dùng mặc định
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID REFERENCES users(user_id),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       UUID REFERENCES users(user_id),
    row_version      BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_experts_code ON experts (expert_code);      -- BR-2.1.1 (kể cả đã xoá: mã không tái sử dụng)
CREATE INDEX ix_experts_status ON experts (status) WHERE deleted_at IS NULL;
CREATE INDEX ix_experts_name_trgm ON experts USING gin (f_unaccent(lower(full_name)) gin_trgm_ops);
CREATE TRIGGER experts_updated_at BEFORE UPDATE ON experts FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_owner_expert FOREIGN KEY (owner_expert_id) REFERENCES experts(expert_id);
CREATE INDEX ix_documents_owner ON documents (owner_expert_id);

-- FR-2.2
CREATE TABLE expert_educations (
    education_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id         UUID NOT NULL REFERENCES experts(expert_id),
    degree_level_code VARCHAR(30) NOT NULL REFERENCES degree_levels(degree_level_code),
    field_id          UUID REFERENCES education_fields(field_id),
    major             VARCHAR(255),
    institution       VARCHAR(255) NOT NULL,
    graduation_year   SMALLINT CHECK (graduation_year BETWEEN 1950 AND 2100),
    evidence_document_id UUID REFERENCES documents(document_id),  -- BR-2.2.2 bắt buộc khi submit (check ở app)
    verified          BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version       BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_expert_educations_expert ON expert_educations (expert_id);

-- FR-2.3 / BR-2.3.1..3 / BR-DATA-003: kinh nghiệm KHÔNG tự tăng.
-- Số năm = (COALESCE(to_date, verified_until) - from_date). Với kinh nghiệm
-- đang tiếp diễn, chỉ tăng khi người có thẩm quyền xác nhận lại verified_until.
CREATE TABLE expert_experiences (
    experience_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id       UUID NOT NULL REFERENCES experts(expert_id),
    industry_id     UUID REFERENCES industries(industry_id),
    field           VARCHAR(255) NOT NULL,
    position        VARCHAR(255),
    organization    VARCHAR(255),
    from_date       DATE NOT NULL,
    to_date         DATE,
    is_current      BOOLEAN NOT NULL DEFAULT false,
    verified_until  DATE,
    description     TEXT,
    evidence_document_id UUID REFERENCES documents(document_id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_exp_dates CHECK (to_date IS NULL OR to_date >= from_date),
    CONSTRAINT ck_exp_current CHECK ((is_current AND to_date IS NULL) OR (NOT is_current AND to_date IS NOT NULL))
);
CREATE INDEX ix_expert_experiences_expert ON expert_experiences (expert_id);

-- Kinh nghiệm theo Code (mapping kinh nghiệm -> code để làm bằng chứng competency)
CREATE TABLE expert_experience_codes (
    experience_id UUID NOT NULL REFERENCES expert_experiences(experience_id) ON DELETE CASCADE,
    code_id       UUID NOT NULL REFERENCES codes(code_id),
    PRIMARY KEY (experience_id, code_id)
);

-- FR-2.5
CREATE TABLE expert_certificates (
    certificate_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id        UUID NOT NULL REFERENCES experts(expert_id),
    certificate_name VARCHAR(500) NOT NULL,
    certificate_no   VARCHAR(100),
    issuer           VARCHAR(255),
    standard_id      UUID REFERENCES standards(standard_id),   -- VD: LA ISO 9001 (IRCA/Exemplar)
    issued_date      DATE,
    expiry_date      DATE,
    document_id      UUID REFERENCES documents(document_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'VALID'
                     CHECK (status IN ('VALID','EXPIRED','REVOKED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cert_dates CHECK (expiry_date IS NULL OR issued_date IS NULL OR expiry_date >= issued_date)
);
CREATE INDEX ix_expert_certificates_expert ON expert_certificates (expert_id);
CREATE INDEX ix_expert_certificates_expiry ON expert_certificates (expiry_date) WHERE status = 'VALID';

-- FR-2.4
CREATE TABLE expert_trainings (
    training_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id      UUID NOT NULL REFERENCES experts(expert_id),
    training_name  VARCHAR(500) NOT NULL,
    provider       VARCHAR(255),
    standard_id    UUID REFERENCES standards(standard_id),
    training_type  VARCHAR(30) CHECK (training_type IN ('LEAD_AUDITOR','INTERNAL_AUDITOR','TECHNICAL','CALIBRATION','REFRESHER','OTHER')),
    from_date      DATE,
    to_date        DATE,
    hours          NUMERIC(6,1),
    valid_until    DATE,                          -- đào tạo có hạn (FR-15.1)
    certificate_id UUID REFERENCES expert_certificates(certificate_id),
    evidence_document_id UUID REFERENCES documents(document_id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version    BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_expert_trainings_expert ON expert_trainings (expert_id);

-- FR-2.6
CREATE TABLE expert_languages (
    expert_id   UUID NOT NULL REFERENCES experts(expert_id),
    language    VARCHAR(10) NOT NULL,             -- ISO 639-1: vi, en, ja...
    proficiency VARCHAR(20) NOT NULL CHECK (proficiency IN ('BASIC','INTERMEDIATE','FLUENT','NATIVE')),
    can_audit   BOOLEAN NOT NULL DEFAULT false,   -- đủ để đánh giá bằng ngôn ngữ này
    PRIMARY KEY (expert_id, language)
);

-- FR-8.1 Availability: khai báo khoảng KHÔNG khả dụng (nghỉ phép, bận) hoặc khả dụng đặc biệt
CREATE TABLE expert_availabilities (
    availability_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expert_id       UUID NOT NULL REFERENCES experts(expert_id),
    period          TSTZRANGE NOT NULL,
    availability_type VARCHAR(20) NOT NULL CHECK (availability_type IN ('UNAVAILABLE','LEAVE','TRAINING','TENTATIVE')),
    note            VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID REFERENCES users(user_id)
);
CREATE INDEX ix_expert_availabilities ON expert_availabilities USING gist (expert_id, period);
