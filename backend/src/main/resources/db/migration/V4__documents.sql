-- =====================================================================
-- V4: Document Repository (Module 12 - FR-12.1 .. FR-12.3)
-- * File nhị phân lưu ở MinIO/S3 (NFR-ST-01/02); DB chỉ lưu metadata.
-- * Chống upload trùng bằng SHA-256 (BR-12.1.1, BR-2.7.2).
-- * Một document có nhiều version; liên kết đa hình qua document_links
--   (Certificate -> Expert -> Competency -> Approval, BR-12.1.2).
-- =====================================================================

CREATE TABLE document_types (
    document_type_code VARCHAR(50) PRIMARY KEY,   -- EDUCATION, CERTIFICATE, TRAINING, COMPETENCE_TEST, EXPERIENCE, SAMPLING_ONLY, CV, NDA, ...
    document_type_name VARCHAR(255) NOT NULL,
    requires_expiry    BOOLEAN NOT NULL DEFAULT false,   -- BR-2.5.1 kiểm soát hiệu lực
    requires_verification BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE documents (
    document_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type_code VARCHAR(50) NOT NULL REFERENCES document_types(document_type_code),
    title              VARCHAR(500) NOT NULL,
    owner_expert_id    UUID,                          -- FK thêm ở V5 (experts tạo sau)
    source             VARCHAR(20) NOT NULL DEFAULT 'UPLOAD'
                       CHECK (source IN ('UPLOAD','OFFICE_LINK','HR_SYNC')),   -- FR-12.3
    external_ref       VARCHAR(500),                  -- link hồ sơ Văn phòng / HR
    current_version_id UUID,                          -- FK thêm sau khi tạo document_versions
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID REFERENCES users(user_id),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE document_versions (
    version_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id    UUID NOT NULL REFERENCES documents(document_id),
    version_no     INT  NOT NULL,
    file_name      VARCHAR(500) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    file_size      BIGINT NOT NULL CHECK (file_size >= 0),
    storage_bucket VARCHAR(100) NOT NULL,
    storage_key    VARCHAR(1000) NOT NULL,
    sha256         CHAR(64) NOT NULL,
    issued_date    DATE,
    expiry_date    DATE,
    uploaded_by    UUID REFERENCES users(user_id),
    uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_by    UUID REFERENCES users(user_id),
    verified_at    TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                   CHECK (status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','SUPERSEDED','EXPIRED')),
    reject_reason  TEXT,
    CONSTRAINT ux_document_version UNIQUE (document_id, version_no),
    CONSTRAINT ck_doc_dates CHECK (expiry_date IS NULL OR issued_date IS NULL OR expiry_date >= issued_date)
);
-- BR-12.1.1: cùng 1 file (hash) không được upload lại -> app trả về document đã có để link
CREATE UNIQUE INDEX ux_document_versions_sha256 ON document_versions (sha256);
CREATE INDEX ix_document_versions_expiry ON document_versions (expiry_date)
    WHERE status = 'VERIFIED' AND expiry_date IS NOT NULL;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_versions(version_id) DEFERRABLE INITIALLY DEFERRED;

-- Liên kết đa hình: 1 tài liệu dùng cho nhiều đối tượng, không upload lại
CREATE TABLE document_links (
    link_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id        UUID NOT NULL REFERENCES documents(document_id),
    linked_object_type VARCHAR(50) NOT NULL
                       CHECK (linked_object_type IN ('EXPERT','EDUCATION','EXPERIENCE','TRAINING','CERTIFICATE',
                                                     'COMPETENCY','APPROVAL','WITNESS','ANNUAL_REVIEW','RESTRICTION',
                                                     'IMPARTIALITY','ASSESSMENT_EVENT')),
    linked_object_id   UUID NOT NULL,
    purpose            VARCHAR(100),
    linked_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    linked_by          UUID REFERENCES users(user_id),
    CONSTRAINT ux_document_link UNIQUE (document_id, linked_object_type, linked_object_id)
);
CREATE INDEX ix_document_links_object ON document_links (linked_object_type, linked_object_id);
