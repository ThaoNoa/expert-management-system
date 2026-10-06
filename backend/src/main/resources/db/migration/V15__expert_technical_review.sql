-- =====================================================================
-- V15: Thêm bước Chuyên gia trưởng thẩm tra trước khi GĐCN phê duyệt
--   DRAFT     --SUBMIT  (NV hồ sơ,         EXPERT_SUBMIT)              --> SUBMITTED  (chờ thẩm tra)
--   SUBMITTED --REVIEW  (Chuyên gia trưởng, EXPERT_REVIEW, ghi chú)     --> REVIEWED   (chờ GĐCN phê duyệt)
--   SUBMITTED --RETURN  (Chuyên gia trưởng, EXPERT_REVIEW, bắt buộc ghi chú) --> DRAFT
--   REVIEWED  --APPROVE (GĐCN,              EXPERT_APPROVE)             --> ACTIVE
--   REVIEWED  --RETURN  (GĐCN,              EXPERT_APPROVE, bắt buộc)   --> DRAFT
--   Người trình không được tự thẩm tra / tự phê duyệt hồ sơ mình trình.
-- =====================================================================

ALTER TABLE experts DROP CONSTRAINT experts_status_check;
ALTER TABLE experts ADD CONSTRAINT experts_status_check
    CHECK (status IN ('DRAFT','SUBMITTED','REVIEWED','ACTIVE','SUSPENDED','INACTIVE'));

INSERT INTO permissions (permission_code, permission_name, module) VALUES
 ('EXPERT_REVIEW', 'Thẩm tra hồ sơ năng lực chuyên gia (đạt / trả lại)', 'EXPERT');

-- Bỏ phê duyệt thẳng từ SUBMITTED; trả lại ở bước thẩm tra thuộc về Chuyên gia trưởng
DELETE FROM workflow_transitions WHERE workflow = 'EXPERT' AND from_status = 'SUBMITTED' AND to_status = 'ACTIVE';
UPDATE workflow_transitions SET required_permission = 'EXPERT_REVIEW'
 WHERE workflow = 'EXPERT' AND from_status = 'SUBMITTED' AND to_status = 'DRAFT';

INSERT INTO workflow_transitions (workflow, from_status, to_status, action, required_permission, requires_comment, forbid_same_actor_as) VALUES
 ('EXPERT','SUBMITTED','REVIEWED','REVIEW', 'EXPERT_REVIEW',  false, 'SUBMITTER'),
 ('EXPERT','REVIEWED', 'ACTIVE',  'APPROVE','EXPERT_APPROVE', false, 'SUBMITTER'),
 ('EXPERT','REVIEWED', 'DRAFT',   'RETURN', 'EXPERT_APPROVE', true,  NULL);

-- Vai trò QA = Chuyên gia trưởng: thẩm tra, đề xuất; KHÔNG phê duyệt (GĐCN phê duyệt)
UPDATE roles SET role_name = 'Chuyên gia trưởng / Thẩm tra kỹ thuật',
                 description = 'Thẩm tra hồ sơ năng lực (đạt / trả lại), witness, đề xuất đoàn đánh giá'
 WHERE role_code = 'TECHNICAL_REVIEWER';

DELETE FROM role_permissions rp USING roles r, permissions p
 WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id
   AND r.role_code = 'TECHNICAL_REVIEWER' AND p.permission_code IN ('COMPETENCY_APPROVE','WITNESS_APPROVE');

INSERT INTO role_permissions (role_id, permission_id, data_scope)
SELECT r.role_id, p.permission_id, 'ALL'
FROM roles r JOIN permissions p ON p.permission_code = 'EXPERT_REVIEW'
WHERE r.role_code = 'TECHNICAL_REVIEWER'
ON CONFLICT DO NOTHING;
