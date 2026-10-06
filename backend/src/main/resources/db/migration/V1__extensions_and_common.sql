-- =====================================================================
-- EMS - Expert Management System
-- V1: Extensions, schema, common functions
-- Target: PostgreSQL 16
-- Conventions:
--   * PK: UUID (gen_random_uuid())
--   * Enum: VARCHAR + CHECK (JPA @Enumerated(STRING) friendly)
--   * Optimistic lock: row_version (JPA @Version)  -- tránh trùng với "version" nghiệp vụ
--   * Soft delete: deleted_at (chỉ các bảng có yêu cầu)
--   * Audit columns: created_at/created_by/updated_at/updated_by
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- exclusion constraint chống trùng lịch
CREATE EXTENSION IF NOT EXISTS pg_trgm;      -- tìm kiếm tên chuyên gia/khách hàng (ILIKE)
CREATE EXTENSION IF NOT EXISTS unaccent;     -- tìm kiếm tiếng Việt không dấu

-- Hàm unaccent IMMUTABLE để dùng được trong index
CREATE OR REPLACE FUNCTION f_unaccent(text)
RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- Tự cập nhật updated_at
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END $$;

-- Chặn UPDATE/DELETE cho bảng append-only (audit_logs, approval_history, ...)
-- BR-AUD-002, BR-AUD-003, NFR-T-04
CREATE OR REPLACE FUNCTION trg_forbid_modify()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END $$;
