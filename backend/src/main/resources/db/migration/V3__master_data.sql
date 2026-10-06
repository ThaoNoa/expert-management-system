-- =====================================================================
-- V3: Master Data (Module 3 - FR-3.1 .. FR-3.5)
-- Quyết định thiết kế:
--   * Versioning Code theo "code_set" (bộ mã) của từng Scheme. Mỗi lần SOP
--     ban hành bộ mã mới -> tạo code_set mới, bộ cũ giữ nguyên (BR-VER-002/004).
--   * PARENT_COVERS_CHILD cấu hình ở cấp Scheme (BR-3.1.3, BR-COV-005),
--     không đặt ở từng dòng code như bản SRS gốc.
-- =====================================================================

CREATE TABLE schemes (
    scheme_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme_code         VARCHAR(50)  NOT NULL UNIQUE,
    scheme_name         VARCHAR(255) NOT NULL,
    description         TEXT,
    parent_covers_child BOOLEAN      NOT NULL DEFAULT false,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version         BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE standards (
    standard_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    standard_code VARCHAR(50)  NOT NULL UNIQUE,          -- ISO9001, ISO22000, FAMIQS
    standard_name VARCHAR(255) NOT NULL,
    scheme_id     UUID NOT NULL REFERENCES schemes(scheme_id),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version   BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_standards_scheme ON standards (scheme_id);

-- BR-3.2.1 / BR-VER-001
CREATE TABLE standard_versions (
    standard_version_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    standard_id         UUID NOT NULL REFERENCES standards(standard_id),
    version             VARCHAR(50) NOT NULL,          -- "2015", "6.0"...
    effective_from      DATE NOT NULL,
    effective_to        DATE,                          -- VD: Fami-QS hiệu lực 4 năm
    transition_end      DATE,                          -- hết giai đoạn chuyển đổi
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('DRAFT','ACTIVE','TRANSITION','WITHDRAWN')),
    CONSTRAINT ux_standard_version UNIQUE (standard_id, version),
    CONSTRAINT ck_standard_version_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- Bộ mã (version của cây Code) theo Scheme
CREATE TABLE code_sets (
    code_set_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme_id      UUID NOT NULL REFERENCES schemes(scheme_id),
    version        VARCHAR(50) NOT NULL,              -- "2026.01"
    effective_from DATE NOT NULL,
    effective_to   DATE,
    source_ref     VARCHAR(255),                      -- SOP / IAF ID ban hành
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ux_code_set UNIQUE (scheme_id, version)
);
-- Mỗi scheme chỉ có tối đa 1 bộ mã ACTIVE
CREATE UNIQUE INDEX ux_code_set_active ON code_sets (scheme_id) WHERE status = 'ACTIVE';

-- FR-3.1 cây Code (Master Code library - BR-3.1.5)
CREATE TABLE codes (
    code_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_set_id  UUID NOT NULL REFERENCES code_sets(code_set_id),
    code_value   VARCHAR(50)  NOT NULL,          -- 1192, A, AI, SP01...
    code_name    VARCHAR(500) NOT NULL,
    parent_id    UUID REFERENCES codes(code_id),
    level        SMALLINT     NOT NULL DEFAULT 1,
    path         VARCHAR(1000) NOT NULL,         -- materialized path: /A/AI/  -> query cây nhanh
    risk_category VARCHAR(20),                   -- dùng cho IAF MD 5 (high/medium/low...)
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    -- mapping sang code của bộ mã cũ (chuyển đổi version)
    replaces_code_id UUID REFERENCES codes(code_id),
    CONSTRAINT ux_code_value UNIQUE (code_set_id, code_value)
);
CREATE INDEX ix_codes_parent ON codes (parent_id);
CREATE INDEX ix_codes_path   ON codes (path varchar_pattern_ops);

CREATE TABLE industries (
    industry_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    industry_code VARCHAR(50)  NOT NULL UNIQUE,
    industry_name VARCHAR(255) NOT NULL,
    description   TEXT
);

CREATE TABLE activities (
    activity_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_code VARCHAR(50)  NOT NULL UNIQUE,
    activity_name VARCHAR(255) NOT NULL,
    description   TEXT
);

-- FR-3.5: có toạ độ để tính khoảng cách (BR-8.2.3, BR-WARN-003)
CREATE TABLE locations (
    location_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_name VARCHAR(255) NOT NULL,
    province      VARCHAR(100),
    country       VARCHAR(2)   NOT NULL DEFAULT 'VN',
    region        VARCHAR(50),                     -- Bắc / Trung / Nam
    latitude      NUMERIC(9,6),
    longitude     NUMERIC(9,6)
);

-- Ma trận thời gian di chuyển giữa tỉnh (dùng cho travel buffer)
CREATE TABLE travel_matrix (
    from_province VARCHAR(100) NOT NULL,
    to_province   VARCHAR(100) NOT NULL,
    travel_hours  NUMERIC(5,1) NOT NULL,
    PRIMARY KEY (from_province, to_province)
);

-- BR-2.2.1: Master data cho giáo dục
CREATE TABLE education_fields (
    field_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_code VARCHAR(50)  NOT NULL UNIQUE,
    field_name VARCHAR(255) NOT NULL,
    parent_id  UUID REFERENCES education_fields(field_id)
);

CREATE TABLE degree_levels (
    degree_level_code VARCHAR(30) PRIMARY KEY,     -- COLLEGE, BACHELOR, ENGINEER, MASTER, PHD
    degree_level_name VARCHAR(100) NOT NULL,
    rank_order        SMALLINT NOT NULL
);

-- Mapping Industry/Activity -> Code (giúp gợi ý code từ ngành nghề KH)
CREATE TABLE code_industry_map (
    code_id     UUID NOT NULL REFERENCES codes(code_id),
    industry_id UUID NOT NULL REFERENCES industries(industry_id),
    PRIMARY KEY (code_id, industry_id)
);
