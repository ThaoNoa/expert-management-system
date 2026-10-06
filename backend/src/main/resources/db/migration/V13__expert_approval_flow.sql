-- =====================================================================
-- V13: Quy trình phê duyệt hồ sơ năng lực chuyên gia (theo yêu cầu VinaCert)
--   NV hồ sơ lập hồ sơ  ->  Trình duyệt  ->  GĐCN Phê duyệt (Hoạt động)
--                                         ->  GĐCN Trả lại / yêu cầu bổ sung (về Nháp, bắt buộc ghi nội dung)
--   Không còn kích hoạt thẳng từ Nháp. Người trình không được tự phê duyệt.
--   Dừng đánh giá có thời hạn: suspended_until = ngày cuối cùng bị dừng; hết hạn hệ thống tự mở lại.
-- =====================================================================

ALTER TABLE experts DROP CONSTRAINT experts_status_check;
ALTER TABLE experts ADD CONSTRAINT experts_status_check
    CHECK (status IN ('DRAFT','SUBMITTED','ACTIVE','SUSPENDED','INACTIVE'));

ALTER TABLE experts ADD COLUMN suspended_until DATE;
COMMENT ON COLUMN experts.suspended_until IS 'Ngày cuối cùng chuyên gia bị dừng đánh giá; NULL = dừng tới khi GĐCN mở lại';

DELETE FROM workflow_transitions WHERE workflow = 'EXPERT' AND from_status = 'DRAFT' AND to_status = 'ACTIVE';

INSERT INTO workflow_transitions (workflow, from_status, to_status, action, required_permission, requires_comment, forbid_same_actor_as) VALUES
 ('EXPERT','DRAFT',     'SUBMITTED','SUBMIT',  'EXPERT_EDIT',    false, NULL),
 ('EXPERT','SUBMITTED', 'ACTIVE',   'APPROVE', 'EXPERT_APPROVE', false, 'SUBMITTER'),
 ('EXPERT','SUBMITTED', 'DRAFT',    'RETURN',  'EXPERT_APPROVE', true,  NULL);

-- Tên vai trò theo cách gọi tại đơn vị (mã giữ nguyên)
UPDATE roles SET role_name = 'Nhân viên hồ sơ chuyên gia' WHERE role_code = 'CERTIFICATION_MANAGER';
UPDATE roles SET role_name = 'Giám đốc chứng nhận (GĐCN)' WHERE role_code = 'CERTIFICATION_DIRECTOR';
