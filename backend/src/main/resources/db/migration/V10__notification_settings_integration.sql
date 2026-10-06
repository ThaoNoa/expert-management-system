-- =====================================================================
-- V10: Notification, System Settings, Integration Log (Module 15, 16, 17)
-- =====================================================================

CREATE TABLE system_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value JSONB NOT NULL,
    value_type    VARCHAR(20) NOT NULL CHECK (value_type IN ('INT','DECIMAL','BOOLEAN','STRING','JSON')),
    category      VARCHAR(50) NOT NULL,
    description   TEXT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    UUID REFERENCES users(user_id)
);

CREATE TABLE notification_templates (
    template_code VARCHAR(100) PRIMARY KEY,
    channel       VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','IN_APP')),
    locale        VARCHAR(10) NOT NULL DEFAULT 'vi',
    subject       VARCHAR(500) NOT NULL,
    body          TEXT NOT NULL,                 -- Mustache/Thymeleaf template
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(user_id),
    template_code   VARCHAR(100) REFERENCES notification_templates(template_code),
    channel         VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','IN_APP')),
    severity        VARCHAR(20) NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO','WARNING','HIGH','CRITICAL')),
    title           VARCHAR(500) NOT NULL,
    content         TEXT NOT NULL,
    object_type     VARCHAR(50),
    object_id       UUID,
    -- chống gửi trùng cảnh báo hằng ngày: VD "COMP_EXPIRY:<id>:30"
    dedup_key       VARCHAR(200),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED','READ')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    retry_count     SMALLINT NOT NULL DEFAULT 0,
    error_message   TEXT
);
CREATE UNIQUE INDEX ux_notifications_dedup ON notifications (user_id, channel, dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX ix_notifications_pending ON notifications (created_at) WHERE status = 'PENDING';

CREATE TABLE integration_logs (
    log_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    integration_type VARCHAR(50) NOT NULL,        -- PLANNING_IN, PLANNING_CALLBACK, HR_SYNC, MAIL
    direction        VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND','OUTBOUND')),
    endpoint         VARCHAR(500),
    http_status      SMALLINT,
    request_payload  JSONB,
    response_payload JSONB,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS','FAILED','RETRYING')),
    error_message    TEXT,
    duration_ms      INT,
    correlation_id   VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_integration_logs_time ON integration_logs USING brin (created_at);

-- Spring Batch / ShedLock: chống chạy trùng job khi scale ngang (NFR-SC-06)
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
