-- =====================================================================
-- V2: Identity & Organization (Module 1 - FR-1.1 .. FR-1.5)
-- =====================================================================

CREATE TABLE departments (
    department_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_code VARCHAR(50)  NOT NULL UNIQUE,
    department_name VARCHAR(255) NOT NULL,
    parent_id       UUID REFERENCES departments(department_id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE users (
    user_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username         VARCHAR(100) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255),                 -- BCrypt; NULL nếu dùng SSO
    full_name        VARCHAR(255) NOT NULL,
    department_id    UUID REFERENCES departments(department_id),
    position         VARCHAR(255),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE','DISABLED','LOCKED')),
    mfa_enabled      BOOLEAN      NOT NULL DEFAULT false,      -- NFR-S-05 MFA-ready
    mfa_secret       VARCHAR(255),
    failed_login_count INT        NOT NULL DEFAULT 0,
    last_login_at    TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,                               -- BR-1.1.2 soft delete
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       UUID,
    row_version      BIGINT       NOT NULL DEFAULT 0
);
-- BR-1.1.1: email unique (không phân biệt hoa thường, chỉ trong user chưa xoá)
CREATE UNIQUE INDEX ux_users_email    ON users (lower(email))    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_users_username ON users (lower(username)) WHERE deleted_at IS NULL;
CREATE TRIGGER users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE roles (
    role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code   VARCHAR(50)  NOT NULL UNIQUE,
    role_name   VARCHAR(255) NOT NULL,
    description TEXT,
    is_system   BOOLEAN      NOT NULL DEFAULT false,   -- role mặc định, không cho xoá
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE permissions (
    permission_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    permission_name VARCHAR(255) NOT NULL,
    module          VARCHAR(50)  NOT NULL,
    description     TEXT
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(permission_id),
    -- Phạm vi dữ liệu: ALL = toàn bộ, OWN = chỉ dữ liệu của mình (BR-SOD-001/002)
    data_scope    VARCHAR(20) NOT NULL DEFAULT 'ALL' CHECK (data_scope IN ('ALL','DEPARTMENT','OWN')),
    PRIMARY KEY (role_id, permission_id)
);

-- BR-1.2.2: Không xoá Role đang được gán -> FK RESTRICT
CREATE TABLE user_roles (
    user_id     UUID NOT NULL REFERENCES users(user_id),
    role_id     UUID NOT NULL REFERENCES roles(role_id) ON DELETE RESTRICT,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by  UUID REFERENCES users(user_id),
    PRIMARY KEY (user_id, role_id)
);

-- FR-1.5 Assessment Role - tách biệt hoàn toàn với System Role (BR-1.5.1)
CREATE TABLE assessment_roles (
    assessment_role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code          VARCHAR(20)  NOT NULL UNIQUE,     -- LA / AU / TE / OBS / TRAINEE
    role_name          VARCHAR(255) NOT NULL,
    description        TEXT,
    counts_for_coverage BOOLEAN     NOT NULL DEFAULT true,  -- Observer/Trainee không tính coverage
    sort_order         INT          NOT NULL DEFAULT 0,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE'))
);

-- Refresh token / phiên đăng nhập (JWT stateless + revoke list)
CREATE TABLE refresh_tokens (
    token_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(user_id),
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    ip_address  INET,
    user_agent  VARCHAR(500)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
