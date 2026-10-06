-- =====================================================================
-- V12: Seed reference data (role, permission, assessment role, workflow,
--      rule, settings, document type, degree level, expert code sequence)
-- Dữ liệu nghiệp vụ (Code, Standard, chuyên gia) KHÔNG seed ở đây - import riêng.
-- =====================================================================

-- ---------- System roles (FR-1.2) ----------
INSERT INTO roles (role_code, role_name, description, is_system) VALUES
 ('SUPER_ADMIN',             'Super Admin',              'Quản trị hệ thống, phân quyền, cấu hình', true),
 ('CERTIFICATION_MANAGER',   'Admin / Certification Manager', 'Quản lý chuyên gia, lập đoàn', true),
 ('TECHNICAL_REVIEWER',      'QA / Technical Reviewer',  'Thẩm tra năng lực, witness', true),
 ('DOCUMENT_CONTROLLER',     'Document Controller',      'Quản lý hồ sơ, tài liệu', true),
 ('CERTIFICATION_DIRECTOR',  'Giám đốc chứng nhận',      'Phê duyệt, dừng/mở chuyên gia', true),
 ('EXPERT',                  'Chuyên gia',               'Xem/cập nhật hồ sơ cá nhân, xác nhận lịch', true);

-- ---------- Permissions (FR-1.3) ----------
INSERT INTO permissions (permission_code, permission_name, module) VALUES
 ('USER_MANAGE',          'Quản lý người dùng & phân quyền', 'IDENTITY'),
 ('MASTER_DATA_MANAGE',   'Quản lý master data',             'MASTER'),
 ('EXPERT_VIEW',          'Xem chuyên gia',                  'EXPERT'),
 ('EXPERT_CREATE',        'Tạo chuyên gia',                  'EXPERT'),
 ('EXPERT_EDIT',          'Sửa hồ sơ chuyên gia',            'EXPERT'),
 ('EXPERT_APPROVE',       'Kích hoạt/khôi phục chuyên gia',  'EXPERT'),
 ('EXPERT_ASSIGN',        'Phân công chuyên gia',            'EXPERT'),
 ('EXPERT_SUSPEND',       'Dừng/mở chuyên gia',              'EXPERT'),
 ('DOCUMENT_MANAGE',      'Quản lý tài liệu',                'DOCUMENT'),
 ('DOCUMENT_VERIFY',      'Xác minh tài liệu',               'DOCUMENT'),
 ('COMPETENCY_VIEW',      'Xem năng lực',                    'COMPETENCY'),
 ('COMPETENCY_SUBMIT',    'Nộp hồ sơ năng lực',              'COMPETENCY'),
 ('COMPETENCY_REVIEW',    'Thẩm tra năng lực',               'COMPETENCY'),
 ('COMPETENCY_APPROVE',   'Phê duyệt năng lực',              'COMPETENCY'),
 ('RESTRICTION_CREATE',   'Đề xuất hạn chế',                 'COMPETENCY'),
 ('MATCHING_RUN',         'Chạy matching',                   'MATCHING'),
 ('TEAM_CREATE',          'Lập đoàn',                        'ASSESSMENT'),
 ('TEAM_APPROVE',         'Duyệt đoàn',                      'ASSESSMENT'),
 ('SCHEDULE_MANAGE',      'Quản lý lịch & manday',           'ASSESSMENT'),
 ('IMPARTIALITY_REVIEW',  'Xem xét xung đột lợi ích',        'GOVERNANCE'),
 ('WITNESS_CREATE',       'Lập kế hoạch witness',            'MONITORING'),
 ('WITNESS_APPROVE',      'Đánh giá witness',                'MONITORING'),
 ('ANNUAL_REVIEW_MANAGE', 'Annual review',                   'MONITORING'),
 ('RULE_MANAGE',          'Cấu hình rule',                   'SETTINGS'),
 ('SETTING_MANAGE',       'Cấu hình hệ thống',               'SETTINGS'),
 ('REPORT_VIEW',          'Xem báo cáo',                     'REPORT'),
 ('AUDIT_LOG_VIEW',       'Xem audit log',                   'GOVERNANCE'),
 ('INTEGRATION_CALL',     'Gọi API tích hợp (system client)','INTEGRATION');

-- ---------- Role -> Permission (SRS 9.1, BẢN NHÁP - cần xác nhận) ----------
WITH m(role_code, perms, scope) AS (VALUES
 ('SUPER_ADMIN', ARRAY['USER_MANAGE','MASTER_DATA_MANAGE','RULE_MANAGE','SETTING_MANAGE','AUDIT_LOG_VIEW','EXPERT_VIEW','REPORT_VIEW'], 'ALL'),
 ('CERTIFICATION_MANAGER', ARRAY['EXPERT_VIEW','EXPERT_CREATE','EXPERT_EDIT','EXPERT_ASSIGN','DOCUMENT_MANAGE','COMPETENCY_VIEW','COMPETENCY_SUBMIT',
        'RESTRICTION_CREATE','MATCHING_RUN','TEAM_CREATE','SCHEDULE_MANAGE','WITNESS_CREATE','REPORT_VIEW','AUDIT_LOG_VIEW','MASTER_DATA_MANAGE'], 'ALL'),
 ('TECHNICAL_REVIEWER', ARRAY['EXPERT_VIEW','COMPETENCY_VIEW','COMPETENCY_REVIEW','COMPETENCY_APPROVE','DOCUMENT_VERIFY','MATCHING_RUN','TEAM_CREATE',
        'WITNESS_CREATE','WITNESS_APPROVE','ANNUAL_REVIEW_MANAGE','IMPARTIALITY_REVIEW','REPORT_VIEW','AUDIT_LOG_VIEW'], 'ALL'),
 -- BR-SOD-004: Document Controller không phê duyệt competency
 ('DOCUMENT_CONTROLLER', ARRAY['EXPERT_VIEW','EXPERT_CREATE','EXPERT_EDIT','DOCUMENT_MANAGE','DOCUMENT_VERIFY','COMPETENCY_VIEW','COMPETENCY_SUBMIT',
        'MATCHING_RUN','TEAM_CREATE','WITNESS_CREATE'], 'ALL'),
 -- BR-SOD-003: Giám đốc phê duyệt nhưng không có USER_MANAGE/SETTING_MANAGE
 ('CERTIFICATION_DIRECTOR', ARRAY['EXPERT_VIEW','EXPERT_EDIT','EXPERT_APPROVE','EXPERT_SUSPEND','COMPETENCY_VIEW','COMPETENCY_APPROVE','MATCHING_RUN',
        'TEAM_CREATE','TEAM_APPROVE','WITNESS_APPROVE','ANNUAL_REVIEW_MANAGE','IMPARTIALITY_REVIEW','REPORT_VIEW','AUDIT_LOG_VIEW'], 'ALL'),
 -- BR-SOD-001/002: Expert chỉ dữ liệu của mình
 ('EXPERT', ARRAY['EXPERT_VIEW','EXPERT_EDIT','COMPETENCY_VIEW','COMPETENCY_SUBMIT','DOCUMENT_MANAGE'], 'OWN')
)
INSERT INTO role_permissions (role_id, permission_id, data_scope)
SELECT r.role_id, p.permission_id, m.scope
FROM m JOIN roles r ON r.role_code = m.role_code
JOIN permissions p ON p.permission_code = ANY (m.perms);

-- ---------- Assessment roles (FR-1.5) ----------
INSERT INTO assessment_roles (role_code, role_name, counts_for_coverage, sort_order) VALUES
 ('LA',      'Lead Auditor - Trưởng đoàn',     true,  1),
 ('AU',      'Auditor - Đánh giá viên',        true,  2),
 ('TE',      'Technical Expert - CG kỹ thuật', true,  3),
 ('OBS',     'Observer - Quan sát viên',       false, 4),
 ('TRAINEE', 'Auditor in Training',            false, 5);

-- ---------- Workflow transitions ----------
INSERT INTO workflow_transitions (workflow, from_status, to_status, action, required_permission, requires_comment, forbid_same_actor_as) VALUES
 -- COMPETENCY (FR-4.2)
 ('COMPETENCY','DRAFT','SUBMITTED','SUBMIT','COMPETENCY_SUBMIT',false,NULL),
 ('COMPETENCY','NEED_REVISION','SUBMITTED','SUBMIT','COMPETENCY_SUBMIT',false,NULL),
 ('COMPETENCY','SUBMITTED','DRAFT','WITHDRAW','COMPETENCY_SUBMIT',false,NULL),
 ('COMPETENCY','SUBMITTED','UNDER_REVIEW','START_REVIEW','COMPETENCY_REVIEW',false,NULL),
 ('COMPETENCY','UNDER_REVIEW','NEED_REVISION','RETURN','COMPETENCY_REVIEW',true,NULL),
 ('COMPETENCY','UNDER_REVIEW','APPROVED','APPROVE','COMPETENCY_APPROVE',false,'SUBMITTER'),
 ('COMPETENCY','UNDER_REVIEW','REJECTED','REJECT','COMPETENCY_APPROVE',true,'SUBMITTER'),
 ('COMPETENCY','APPROVED','REVIEW_REQUIRED','FLAG_REVIEW',NULL,true,NULL),
 ('COMPETENCY','APPROVED','SUSPENDED','SUSPEND','EXPERT_SUSPEND',true,NULL),
 ('COMPETENCY','APPROVED','EXPIRED','EXPIRE',NULL,false,NULL),
 ('COMPETENCY','APPROVED','REVOKED','REVOKE','COMPETENCY_APPROVE',true,NULL),
 ('COMPETENCY','APPROVED','SUPERSEDED','SUPERSEDE',NULL,false,NULL),
 ('COMPETENCY','REVIEW_REQUIRED','APPROVED','CONFIRM','COMPETENCY_APPROVE',true,NULL),
 ('COMPETENCY','REVIEW_REQUIRED','SUSPENDED','SUSPEND','EXPERT_SUSPEND',true,NULL),
 ('COMPETENCY','REVIEW_REQUIRED','REVOKED','REVOKE','COMPETENCY_APPROVE',true,NULL),
 ('COMPETENCY','REVIEW_REQUIRED','EXPIRED','EXPIRE',NULL,false,NULL),
 ('COMPETENCY','REVIEW_REQUIRED','SUPERSEDED','SUPERSEDE',NULL,false,NULL),
 ('COMPETENCY','SUSPENDED','APPROVED','REINSTATE','EXPERT_SUSPEND',true,NULL),
 ('COMPETENCY','SUSPENDED','REVOKED','REVOKE','COMPETENCY_APPROVE',true,NULL),
 ('COMPETENCY','SUSPENDED','EXPIRED','EXPIRE',NULL,false,NULL),
 ('COMPETENCY','SUSPENDED','SUPERSEDED','SUPERSEDE',NULL,false,NULL),
 ('COMPETENCY','EXPIRED','SUPERSEDED','SUPERSEDE',NULL,false,NULL),
 -- EXPERT (BR-4.3.1)
 ('EXPERT','DRAFT','ACTIVE','ACTIVATE','EXPERT_APPROVE',false,NULL),
 ('EXPERT','ACTIVE','SUSPENDED','SUSPEND','EXPERT_SUSPEND',true,NULL),
 ('EXPERT','SUSPENDED','ACTIVE','REINSTATE','EXPERT_SUSPEND',true,NULL),
 ('EXPERT','ACTIVE','INACTIVE','DEACTIVATE','EXPERT_APPROVE',true,NULL),
 ('EXPERT','SUSPENDED','INACTIVE','DEACTIVATE','EXPERT_APPROVE',true,NULL),
 ('EXPERT','INACTIVE','ACTIVE','REACTIVATE','EXPERT_APPROVE',true,NULL),
 -- RESTRICTION (FR-4.5) - maker/checker
 ('RESTRICTION','DRAFT','PENDING_APPROVAL','SUBMIT','RESTRICTION_CREATE',false,NULL),
 ('RESTRICTION','DRAFT','CANCELLED','CANCEL','RESTRICTION_CREATE',false,NULL),
 ('RESTRICTION','PENDING_APPROVAL','ACTIVE','APPROVE','EXPERT_SUSPEND',false,'CREATOR'),
 ('RESTRICTION','PENDING_APPROVAL','DRAFT','RETURN','EXPERT_SUSPEND',true,NULL),
 ('RESTRICTION','PENDING_APPROVAL','CANCELLED','REJECT','EXPERT_SUSPEND',true,NULL),
 ('RESTRICTION','ACTIVE','RELEASED','RELEASE','EXPERT_SUSPEND',true,NULL),
 ('RESTRICTION','ACTIVE','EXPIRED','EXPIRE',NULL,false,NULL),
 -- TEAM (FR-7.2, 7.5)
 ('TEAM','DRAFT','SUBMITTED','SUBMIT','TEAM_CREATE',false,NULL),
 ('TEAM','SUBMITTED','DRAFT','RETURN','TEAM_APPROVE',true,NULL),
 ('TEAM','SUBMITTED','APPROVED','APPROVE','TEAM_APPROVE',false,'SUBMITTER'),
 ('TEAM','APPROVED','NOTIFIED','NOTIFY_CUSTOMER','TEAM_CREATE',false,NULL),
 ('TEAM','NOTIFIED','ACCEPTED','CUSTOMER_ACCEPT','TEAM_CREATE',false,NULL),
 ('TEAM','NOTIFIED','OBJECTED','CUSTOMER_OBJECT','TEAM_CREATE',true,NULL),
 ('TEAM','OBJECTED','ACCEPTED','KEEP_WITH_JUSTIFICATION','TEAM_APPROVE',true,NULL),
 ('TEAM','OBJECTED','SUPERSEDED','CHANGE_TEAM','TEAM_CREATE',true,NULL),
 ('TEAM','ACCEPTED','SUPERSEDED','CHANGE_TEAM','TEAM_APPROVE',true,NULL),
 ('TEAM','ACCEPTED','COMPLETED','COMPLETE',NULL,false,NULL),
 ('TEAM','DRAFT','CANCELLED','CANCEL','TEAM_CREATE',false,NULL),
 ('TEAM','SUBMITTED','CANCELLED','CANCEL','TEAM_APPROVE',true,NULL),
 ('TEAM','APPROVED','CANCELLED','CANCEL','TEAM_APPROVE',true,NULL),
 ('TEAM','NOTIFIED','CANCELLED','CANCEL','TEAM_APPROVE',true,NULL),
 ('TEAM','ACCEPTED','CANCELLED','CANCEL','TEAM_APPROVE',true,NULL),
 ('TEAM','OBJECTED','CANCELLED','CANCEL','TEAM_APPROVE',true,NULL),
 -- WITNESS plan (FR-10.1..10.3)
 ('WITNESS','PLANNED','SCHEDULED','SCHEDULE','WITNESS_CREATE',false,NULL),
 ('WITNESS','SCHEDULED','PLANNED','UNSCHEDULE','WITNESS_CREATE',true,NULL),
 ('WITNESS','SCHEDULED','COMPLETED','COMPLETE','WITNESS_APPROVE',false,NULL),
 ('WITNESS','PLANNED','OVERDUE','MARK_OVERDUE',NULL,false,NULL),
 ('WITNESS','SCHEDULED','OVERDUE','MARK_OVERDUE',NULL,false,NULL),
 ('WITNESS','OVERDUE','SCHEDULED','SCHEDULE','WITNESS_CREATE',false,NULL),
 ('WITNESS','PLANNED','CANCELLED','CANCEL','WITNESS_CREATE',true,NULL),
 ('WITNESS','SCHEDULED','CANCELLED','CANCEL','WITNESS_CREATE',true,NULL),
 -- ANNUAL_REVIEW (FR-11)
 ('ANNUAL_REVIEW','OPEN','SELF_UPDATED','SELF_UPDATE','COMPETENCY_SUBMIT',false,NULL),
 ('ANNUAL_REVIEW','OPEN','UNDER_REVIEW','START_REVIEW','ANNUAL_REVIEW_MANAGE',false,NULL),
 ('ANNUAL_REVIEW','SELF_UPDATED','UNDER_REVIEW','START_REVIEW','ANNUAL_REVIEW_MANAGE',false,NULL),
 ('ANNUAL_REVIEW','UNDER_REVIEW','OPEN','RETURN','ANNUAL_REVIEW_MANAGE',true,NULL),
 ('ANNUAL_REVIEW','UNDER_REVIEW','COMPLETED','COMPLETE','ANNUAL_REVIEW_MANAGE',false,NULL),
 ('ANNUAL_REVIEW','OPEN','OVERDUE','MARK_OVERDUE',NULL,false,NULL),
 ('ANNUAL_REVIEW','SELF_UPDATED','OVERDUE','MARK_OVERDUE',NULL,false,NULL),
 ('ANNUAL_REVIEW','OVERDUE','UNDER_REVIEW','START_REVIEW','ANNUAL_REVIEW_MANAGE',false,NULL),
 ('ANNUAL_REVIEW','OPEN','CANCELLED','CANCEL','ANNUAL_REVIEW_MANAGE',true,NULL),
 -- DOCUMENT version (FR-12.2)
 ('DOCUMENT','PENDING_VERIFICATION','VERIFIED','VERIFY','DOCUMENT_VERIFY',false,'UPLOADER'),
 ('DOCUMENT','PENDING_VERIFICATION','REJECTED','REJECT','DOCUMENT_VERIFY',true,NULL),
 ('DOCUMENT','VERIFIED','SUPERSEDED','SUPERSEDE',NULL,false,NULL),
 ('DOCUMENT','VERIFIED','EXPIRED','EXPIRE',NULL,false,NULL),
 -- CUSTOMER_NOTIFICATION (FR-7.5)
 ('CUSTOMER_NOTIFICATION','DRAFT','SENT','SEND','TEAM_CREATE',false,NULL),
 ('CUSTOMER_NOTIFICATION','SENT','ACCEPTED','RECORD_ACCEPT','TEAM_CREATE',false,NULL),
 ('CUSTOMER_NOTIFICATION','SENT','OBJECTED','RECORD_OBJECT','TEAM_CREATE',true,NULL),
 ('CUSTOMER_NOTIFICATION','SENT','NO_RESPONSE','MARK_NO_RESPONSE',NULL,false,NULL),
 ('CUSTOMER_NOTIFICATION','NO_RESPONSE','ACCEPTED','RECORD_ACCEPT','TEAM_CREATE',false,NULL),
 ('CUSTOMER_NOTIFICATION','NO_RESPONSE','OBJECTED','RECORD_OBJECT','TEAM_CREATE',true,NULL);

-- ---------- Rules (SRS mục 5) ----------
INSERT INTO rule_definitions (rule_code, rule_name, rule_group, severity, priority, evaluator_key, parameters, message_template, version, effective_from) VALUES
 ('BR-MATCH-001','Expert phải có Competency','MATCHING','BLOCKER',10,'hasCompetencyRule','{}','Chưa có năng lực cho {standard}','2026.01','2026-01-01'),
 ('BR-MATCH-002','Competency phải Approved','MATCHING','BLOCKER',20,'approvedCompetencyRule','{}','Năng lực chưa được phê duyệt (trạng thái {status})','2026.01','2026-01-01'),
 ('BR-MATCH-003','Code phải phù hợp','MATCHING','BLOCKER',30,'codeMatchRule','{}','Không có năng lực cho code {codes}','2026.01','2026-01-01'),
 ('BR-MATCH-004','Standard phải phù hợp','MATCHING','BLOCKER',40,'standardMatchRule','{}','Không có năng lực cho tiêu chuẩn {standard}','2026.01','2026-01-01'),
 ('BR-MATCH-005','Role phải phù hợp','MATCHING','BLOCKER',50,'roleMatchRule','{}','Không có năng lực vai trò {role}','2026.01','2026-01-01'),
 ('BR-MATCH-006','Expert không Suspended/không bị dừng','MATCHING','BLOCKER',60,'notSuspendedRule','{}','Chuyên gia đang tạm dừng / dừng đánh giá','2026.01','2026-01-01'),
 ('BR-MATCH-007','Competency không hết hạn','MATCHING','BLOCKER',70,'notExpiredRule','{}','Năng lực hết hạn ngày {effectiveTo}','2026.01','2026-01-01'),
 ('BR-MATCH-008','Expert phải Available','MATCHING','BLOCKER',80,'availabilityRule','{}','Trùng lịch {from} - {to}','2026.01','2026-01-01'),
 ('BR-MATCH-009','Không có Conflict of Interest','MATCHING','BLOCKER',90,'conflictOfInterestRule','{}','Có xung đột lợi ích với khách hàng {customer}','2026.01','2026-01-01'),
 ('BR-WARN-001','Đã đánh giá khách hàng này','MATCHING','WARNING',110,'previousCustomerRule','{"lookbackYears":3}','Đã từng đánh giá khách hàng này ({count} lần)','2026.01','2026-01-01'),
 ('BR-WARN-002','Lịch gần sát nhau','SCHEDULE','WARNING',120,'tightScheduleRule','{"minGapDays":1}','Lịch liền kề cuộc {event}','2026.01','2026-01-01'),
 ('BR-WARN-003','Di chuyển địa lý khó','SCHEDULE','WARNING',130,'travelRule','{"maxTravelHours":4}','Cần {hours} giờ di chuyển từ cuộc trước','2026.01','2026-01-01'),
 ('BR-WARN-004','Workload cao','MATCHING','WARNING',140,'workloadRule','{"maxMandaysPerMonth":15}','Đã có {mandays} manday trong tháng','2026.01','2026-01-01'),
 ('BR-WARN-005','Witness gần đến hạn','MATCHING','WARNING',150,'witnessDueRule','{"withinDays":90}','Witness đến hạn {dueDate}','2026.01','2026-01-01'),
 ('BR-WARN-006','Certificate sắp hết hạn','MATCHING','WARNING',160,'certificateExpiryRule','{"withinDays":60}','Chứng chỉ {name} hết hạn {expiryDate}','2026.01','2026-01-01'),
 ('BR-COV-001','Kiểm tra đủ Code/Standard/Role','COVERAGE','BLOCKER',10,'coverageRule','{}','Thiếu: {missing}','2026.01','2026-01-01');

-- ---------- System settings (FR-17.3) ----------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, description) VALUES
 ('alert.expiry.warningDays',     '60',  'INT', 'ALERT', 'BR-4.4.1 Warning'),
 ('alert.expiry.highDays',        '30',  'INT', 'ALERT', 'BR-4.4.2 High Warning'),
 ('alert.expiry.criticalDays',    '7',   'INT', 'ALERT', 'BR-4.4.3 Critical'),
 ('alert.restriction.endingDays', '14',  'INT', 'ALERT', 'Cảnh báo sắp hết thời gian STOP'),
 ('annualReview.leadDays',        '30',  'INT', 'REVIEW', 'Tạo kỳ annual review trước hạn N ngày'),
 ('schedule.travelBufferHours',   '12',  'INT', 'SCHEDULE', 'BR-8.2.2 travel buffer'),
 ('workload.defaultMaxMandaysPerMonth','15','DECIMAL','MATCHING','Ngưỡng workload mặc định'),
 ('security.sessionTimeoutMinutes','30', 'INT', 'SECURITY', 'Session timeout'),
 ('security.passwordMinLength',   '8',   'INT', 'SECURITY', 'Password policy'),
 ('pagination.defaultSize',       '20',  'INT', 'UI', 'NFR-P-02');

-- ---------- Document types (BR-2.7.1) ----------
INSERT INTO document_types (document_type_code, document_type_name, requires_expiry) VALUES
 ('EDUCATION','Học vấn',false), ('CERTIFICATE','Chứng chỉ',true), ('TRAINING','Đào tạo',false),
 ('COMPETENCE_TEST','Kiểm tra năng lực',false), ('EXPERIENCE','Kinh nghiệm',false),
 ('SAMPLING_ONLY','Chỉ lấy mẫu',false), ('CV','Sơ yếu lý lịch',false), ('NDA','Cam kết bảo mật',true),
 ('IMPARTIALITY','Cam kết khách quan',true), ('WITNESS_REPORT','Báo cáo witness',false), ('SOP','SOP',false), ('OTHER','Khác',false);

INSERT INTO degree_levels VALUES
 ('COLLEGE','Cao đẳng',1), ('BACHELOR','Cử nhân',2), ('ENGINEER','Kỹ sư',3), ('MASTER','Thạc sĩ',4), ('PHD','Tiến sĩ',5);

INSERT INTO expert_code_sequences (employment_type, prefix, pad_length) VALUES
 ('FULLTIME','FT-',4), ('PARTTIME','PT-',3);
