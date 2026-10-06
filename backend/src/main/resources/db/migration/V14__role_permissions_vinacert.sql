-- =====================================================================
-- V14: Phân quyền lại theo quy trình VinaCert
--   Văn phòng      : nhập THÔNG TIN NHÂN SỰ (tạo chuyên gia, thông tin chung) + tải lên / xác minh tài liệu
--   NV hồ sơ       : nhập NĂNG LỰC (học vấn, kinh nghiệm + code, đào tạo, chứng chỉ, ngoại ngữ), trình GĐCN,
--                    import, quản lý danh mục
--   GĐCN           : phê duyệt / trả lại, dừng / mở chuyên gia – KHÔNG sửa nội dung hồ sơ
--   Trưởng/phó CN  : xem toàn bộ (chỉ đọc)
--   Chuyên gia     : chỉ hồ sơ của mình – xem, sửa thông tin liên hệ, tải tài liệu bổ sung
-- Quyền mới: EXPERT_COMPETENCY_EDIT, EXPERT_CONTACT_EDIT, EXPERT_SUBMIT.
-- =====================================================================

INSERT INTO permissions (permission_code, permission_name, module) VALUES
 ('EXPERT_COMPETENCY_EDIT', 'Sửa năng lực chuyên gia (học vấn, kinh nghiệm, code, đào tạo, chứng chỉ, ngoại ngữ)', 'EXPERT'),
 ('EXPERT_CONTACT_EDIT',    'Sửa thông tin liên hệ (điện thoại, email, địa chỉ)', 'EXPERT'),
 ('EXPERT_SUBMIT',          'Trình GĐCN phê duyệt hồ sơ chuyên gia', 'EXPERT');

UPDATE permissions SET permission_name = 'Sửa thông tin chung / nhân sự của chuyên gia' WHERE permission_code = 'EXPERT_EDIT';

-- Trình duyệt dùng quyền riêng (Văn phòng sửa được thông tin chung nhưng không trình)
UPDATE workflow_transitions SET required_permission = 'EXPERT_SUBMIT'
 WHERE workflow = 'EXPERT' AND from_status = 'DRAFT' AND to_status = 'SUBMITTED';

-- Vai trò mới: Trưởng / phó phòng chứng nhận (chỉ đọc)
-- (có thể đã được tạo tay / bằng dữ liệu demo trước đó → cập nhật lại cho đúng)
INSERT INTO roles (role_code, role_name, description, is_system) VALUES
 ('HEAD_CERTIFICATION', 'Trưởng / phó phòng chứng nhận', 'Xem, lọc, xuất hồ sơ năng lực chuyên gia (chỉ đọc)', true)
ON CONFLICT (role_code) DO UPDATE SET role_name = EXCLUDED.role_name, description = EXCLUDED.description, is_system = true;

UPDATE roles SET role_name = 'Văn phòng (nhân sự & tài liệu)', description = 'Nhập thông tin nhân sự, tải lên và xác minh tài liệu'
 WHERE role_code = 'DOCUMENT_CONTROLLER';
UPDATE roles SET description = 'Lập hồ sơ năng lực, trình GĐCN, import, quản lý danh mục' WHERE role_code = 'CERTIFICATION_MANAGER';
UPDATE roles SET description = 'Phê duyệt / trả lại hồ sơ, dừng / mở chuyên gia' WHERE role_code = 'CERTIFICATION_DIRECTOR';
UPDATE roles SET description = 'Xem hồ sơ của mình, sửa thông tin liên hệ, tải tài liệu bổ sung' WHERE role_code = 'EXPERT';

-- Gỡ quyền không đúng vai trò
DELETE FROM role_permissions rp USING roles r, permissions p
 WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id AND (
       (r.role_code = 'CERTIFICATION_DIRECTOR' AND p.permission_code IN ('EXPERT_EDIT'))
    OR (r.role_code = 'DOCUMENT_CONTROLLER'    AND p.permission_code IN ('COMPETENCY_SUBMIT','MATCHING_RUN','TEAM_CREATE','WITNESS_CREATE'))
    OR (r.role_code = 'EXPERT'                 AND p.permission_code IN ('EXPERT_EDIT','COMPETENCY_SUBMIT')));

-- Cấp quyền mới
WITH m(role_code, perms, scope) AS (VALUES
 ('CERTIFICATION_MANAGER', ARRAY['EXPERT_COMPETENCY_EDIT','EXPERT_SUBMIT'], 'ALL'),
 ('HEAD_CERTIFICATION',    ARRAY['EXPERT_VIEW','COMPETENCY_VIEW','REPORT_VIEW'], 'ALL'),
 ('EXPERT',                ARRAY['EXPERT_CONTACT_EDIT'], 'OWN')
)
INSERT INTO role_permissions (role_id, permission_id, data_scope)
SELECT r.role_id, p.permission_id, m.scope
FROM m JOIN roles r ON r.role_code = m.role_code
JOIN permissions p ON p.permission_code = ANY (m.perms)
ON CONFLICT DO NOTHING;
