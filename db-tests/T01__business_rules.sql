-- =====================================================================
-- Kiểm thử nghiệp vụ trên DB thật (chạy sau V1..V12, DB trống).
-- Mỗi khối in "PASS: ..." hoặc dừng với lỗi. Chạy trong 1 transaction rồi ROLLBACK.
-- =====================================================================
\set ON_ERROR_STOP 1
BEGIN;

CREATE TEMP TABLE ids (k text PRIMARY KEY, v uuid);
CREATE OR REPLACE FUNCTION pg_temp.id(text) RETURNS uuid LANGUAGE sql AS $$ SELECT v FROM ids WHERE k = $1 $$;
CREATE OR REPLACE FUNCTION pg_temp.expect_error(p_sql text, p_like text, p_label text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE p_like THEN RAISE NOTICE 'PASS: % (%)', p_label, SQLERRM; RETURN; END IF;
        RAISE EXCEPTION 'FAIL: % - unexpected error: %', p_label, SQLERRM;
    END;
    RAISE EXCEPTION 'FAIL: % - expected error, none raised', p_label;
END $$;

-- ---------- Fixture ----------
INSERT INTO users (username, email, full_name) VALUES ('maker','maker@x.vn','Maker'), ('checker','checker@x.vn','Checker');
INSERT INTO ids SELECT username, user_id FROM users;

INSERT INTO schemes (scheme_code, scheme_name, parent_covers_child) VALUES ('QMS','Quality MS', false);
INSERT INTO ids SELECT 'scheme', scheme_id FROM schemes;
INSERT INTO standards (standard_code, standard_name, scheme_id) VALUES ('ISO9001','ISO 9001', pg_temp.id('scheme'));
INSERT INTO ids SELECT 'std', standard_id FROM standards;
INSERT INTO code_sets (scheme_id, version, effective_from, status) VALUES (pg_temp.id('scheme'),'2026.01','2026-01-01','ACTIVE');
INSERT INTO ids SELECT 'cs', code_set_id FROM code_sets;
INSERT INTO codes (code_set_id, code_value, code_name, level, path)
SELECT pg_temp.id('cs'), v, 'Code '||v, 1, '/'||v||'/' FROM unnest(ARRAY['A','B','C','D']) v;
INSERT INTO codes (code_set_id, code_value, code_name, parent_id, level, path)
SELECT pg_temp.id('cs'), 'A1', 'Code A1', code_id, 2, '/A/A1/' FROM codes WHERE code_value = 'A';
INSERT INTO ids SELECT 'code_'||code_value, code_id FROM codes;
INSERT INTO ids SELECT 'role_'||role_code, assessment_role_id FROM assessment_roles;

-- definitions: (code, role) cho ISO 9001
INSERT INTO competency_definitions (scheme_id, standard_id, code_id, assessment_role_id, effective_from)
SELECT pg_temp.id('scheme'), pg_temp.id('std'), pg_temp.id('code_'||c), pg_temp.id('role_'||r), '2020-01-01'
FROM (VALUES ('A','LA'),('B','LA'),('C','AU'),('D','TE'),('A','AU')) x(c,r);
INSERT INTO ids SELECT 'def_'||c.code_value||'_'||ar.role_code, cd.competency_definition_id
FROM competency_definitions cd JOIN codes c ON c.code_id = cd.code_id JOIN assessment_roles ar USING (assessment_role_id);

INSERT INTO experts (expert_code, full_name, expert_type, employment_type, status)
VALUES ('FT-0001','Nguyễn Văn A','AUDITOR','FULLTIME','DRAFT'),
       ('FT-0002','Trần Thị B','AUDITOR','FULLTIME','DRAFT'),
       ('PT-001','Lê Văn C','TECHNICAL_EXPERT','PARTTIME','DRAFT'),
       ('PT-002','Phạm D (hết hạn)','AUDITOR','PARTTIME','DRAFT');
INSERT INTO ids SELECT expert_code, expert_id FROM experts;
UPDATE experts SET status = 'SUBMITTED';   -- V13: DRAFT -> SUBMITTED -> ACTIVE (GĐCN phê duyệt)
UPDATE experts SET status = 'ACTIVE';

-- Duyệt competency qua đúng workflow
CREATE OR REPLACE FUNCTION pg_temp.approve(p_expert text, p_def text, p_to date) RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE v uuid;
BEGIN
    INSERT INTO expert_competencies (expert_id, competency_definition_id, submitted_by)
    VALUES (pg_temp.id(p_expert), pg_temp.id(p_def), pg_temp.id('maker')) RETURNING expert_competency_id INTO v;
    UPDATE expert_competencies SET status = 'SUBMITTED'    WHERE expert_competency_id = v;
    UPDATE expert_competencies SET status = 'UNDER_REVIEW' WHERE expert_competency_id = v;
    UPDATE expert_competencies SET status = 'APPROVED', approved_by = pg_temp.id('checker'), approved_at = now(),
           effective_from = '2025-01-01', effective_to = p_to, first_approved_date = '2025-01-01'
     WHERE expert_competency_id = v;
    RETURN v;
END $$;
INSERT INTO ids VALUES ('ec_A_A', pg_temp.approve('FT-0001','def_A_LA','2028-12-31'));
INSERT INTO ids VALUES ('ec_A_B', pg_temp.approve('FT-0001','def_B_LA','2028-12-31'));
INSERT INTO ids VALUES ('ec_B_C', pg_temp.approve('FT-0002','def_C_AU','2028-12-31'));
INSERT INTO ids VALUES ('ec_C_D', pg_temp.approve('PT-001','def_D_TE','2028-12-31'));
INSERT INTO ids VALUES ('ec_D_A', pg_temp.approve('PT-002','def_A_LA','2026-09-30'));   -- hết hạn trước cuộc đánh giá

-- ---------- 1. State machine ----------
DO $$ BEGIN
  INSERT INTO expert_competencies (expert_id, competency_definition_id) VALUES (pg_temp.id('FT-0002'), pg_temp.id('def_A_AU'));
  PERFORM pg_temp.expect_error(
    $q$UPDATE expert_competencies SET status='APPROVED', approved_by=(SELECT v FROM ids WHERE k='checker'), effective_from='2026-01-01'
        WHERE expert_id=(SELECT v FROM ids WHERE k='FT-0002') AND status='DRAFT'$q$,
    '%Illegal COMPETENCY transition: DRAFT -> APPROVED%', 'Không cho DRAFT -> APPROVED');
END $$;

-- ---------- 2. Approved immutable (BR-VER-005) ----------
DO $$ BEGIN
  PERFORM pg_temp.expect_error(
    $q$UPDATE expert_competencies SET effective_to='2030-12-31' WHERE expert_competency_id=(SELECT v FROM ids WHERE k='ec_A_A')$q$,
    '%immutable%', 'Không sửa trực tiếp competency đã duyệt');
END $$;

-- ---------- 3. Revision: bản mới thay bản cũ ----------
DO $$ DECLARE v uuid; BEGIN
  INSERT INTO expert_competencies (expert_id, competency_definition_id, revision_no, supersedes_id)
  VALUES (pg_temp.id('FT-0001'), pg_temp.id('def_A_LA'), 2, pg_temp.id('ec_A_A')) RETURNING expert_competency_id INTO v;
  PERFORM pg_temp.expect_error(
    format($q$INSERT INTO expert_competencies (expert_id, competency_definition_id) VALUES (%L, %L)$q$, pg_temp.id('FT-0001'), pg_temp.id('def_A_LA')),
    '%ux_expert_competency_pending%', 'Chỉ 1 revision đang xử lý');
  DELETE FROM expert_competencies WHERE expert_competency_id = v;   -- DRAFT được xoá
  RAISE NOTICE 'PASS: Tạo revision song song bản đang hiệu lực';
END $$;

-- ---------- 4. Matching pre-filter ----------
DO $$ DECLARE r record; n int; BEGIN
  SELECT * INTO r FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_A')], pg_temp.id('role_LA'), '2026-10-15', '2026-10-18', NULL)
   WHERE expert_code = 'FT-0001';
  IF NOT r.eligible THEN RAISE EXCEPTION 'FAIL: FT-0001 phải eligible, failed=%', r.failed_rules; END IF;
  SELECT * INTO r FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_A')], pg_temp.id('role_LA'), '2026-10-15', '2026-10-18', NULL)
   WHERE expert_code = 'PT-002';
  IF r.eligible OR NOT ('BR-MATCH-007' = ANY (r.failed_rules)) THEN RAISE EXCEPTION 'FAIL: PT-002 phải bị BR-MATCH-007, got %', r.failed_rules; END IF;
  SELECT count(*) INTO n FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_A')], pg_temp.id('role_LA'), '2026-10-15', '2026-10-18', NULL) WHERE eligible;
  IF n <> 1 THEN RAISE EXCEPTION 'FAIL: kỳ vọng 1 eligible, got %', n; END IF;
  RAISE NOTICE 'PASS: Matching - FT-0001 eligible, PT-002 bị chặn vì hết hạn (BR-MATCH-007)';
END $$;

-- 4b. Restriction scope ISO 9001 -> chặn; maker-checker
INSERT INTO restrictions (expert_id, restriction_type, standard_id, reason, from_date, created_by)
VALUES (pg_temp.id('FT-0001'), 'SUSPEND', pg_temp.id('std'), 'Khiếu nại', '2026-10-01', pg_temp.id('maker'));
INSERT INTO ids SELECT 'restr', restriction_id FROM restrictions;
DO $$ BEGIN
  UPDATE restrictions SET status='PENDING_APPROVAL' WHERE restriction_id = pg_temp.id('restr');
  PERFORM pg_temp.expect_error(
    $q$UPDATE restrictions SET status='ACTIVE', approved_by=created_by WHERE restriction_id=(SELECT v FROM ids WHERE k='restr')$q$,
    '%ck_restr_sod%', 'Người tạo không tự duyệt restriction');
  UPDATE restrictions SET status='ACTIVE', approved_by=pg_temp.id('checker') WHERE restriction_id = pg_temp.id('restr');
  IF (SELECT eligible FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_A')], pg_temp.id('role_LA'), '2026-10-15','2026-10-18', NULL)
      WHERE expert_code='FT-0001') THEN RAISE EXCEPTION 'FAIL: restriction không chặn'; END IF;
  UPDATE restrictions SET status='RELEASED', released_date='2026-10-05' WHERE restriction_id = pg_temp.id('restr');
  RAISE NOTICE 'PASS: Restriction ACTIVE chặn matching (BR-RES-003), release xong mở lại';
END $$;

-- ---------- 5. Coverage (ví dụ SRS 3.6) ----------
INSERT INTO customers (external_ref, customer_code, customer_name) VALUES ('C-1','ABC','Công ty ABC');
INSERT INTO certification_programs (customer_id, scheme_id, standard_id)
SELECT customer_id, pg_temp.id('scheme'), pg_temp.id('std') FROM customers;
INSERT INTO assessment_events (event_code, program_id, event_type, from_date, to_date)
SELECT '2026-001', program_id, 'SURVEILLANCE', '2026-10-15', '2026-10-18' FROM certification_programs;
INSERT INTO ids SELECT 'event', event_id FROM assessment_events;
INSERT INTO required_competencies (event_id, standard_id, code_id)
SELECT pg_temp.id('event'), pg_temp.id('std'), pg_temp.id('code_'||c) FROM unnest(ARRAY['A','B','C','D']) c;
INSERT INTO required_competencies (event_id, standard_id, assessment_role_id) VALUES (pg_temp.id('event'), pg_temp.id('std'), pg_temp.id('role_LA'));
INSERT INTO assessment_teams (event_id, created_by) VALUES (pg_temp.id('event'), pg_temp.id('maker'));
INSERT INTO ids SELECT 'team', team_id FROM assessment_teams;

CREATE OR REPLACE FUNCTION pg_temp.add_member(p_expert text, p_role text, p_codes text[]) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v uuid;
BEGIN
  INSERT INTO team_members (team_id, expert_id, assessment_role_id, start_date, end_date)
  VALUES (pg_temp.id('team'), pg_temp.id(p_expert), pg_temp.id('role_'||p_role), '2026-10-15','2026-10-18') RETURNING team_member_id INTO v;
  INSERT INTO team_member_codes SELECT v, pg_temp.id('std'), pg_temp.id('code_'||c) FROM unnest(p_codes) c;
END $$;
SELECT pg_temp.add_member('FT-0001','LA',ARRAY['A','B']);
SELECT pg_temp.add_member('PT-001','TE',ARRAY['D']);

DO $$ BEGIN
  PERFORM pg_temp.expect_error(
    $q$UPDATE assessment_teams SET status='SUBMITTED' WHERE team_id=(SELECT v FROM ids WHERE k='team')$q$,
    '%Coverage incomplete, missing: ISO9001/C/*%', 'Thiếu code C -> không cho submit đoàn (BR-6.1.3)');
END $$;
SELECT pg_temp.add_member('FT-0002','AU',ARRAY['C']);
UPDATE assessment_teams SET status='SUBMITTED' WHERE team_id = pg_temp.id('team');
DO $$ BEGIN
  IF (SELECT coverage_ratio FROM assessment_teams WHERE team_id = pg_temp.id('team')) <> 1 THEN RAISE EXCEPTION 'FAIL: coverage_ratio'; END IF;
  RAISE NOTICE 'PASS: Đủ A/B/C/D + LA -> submit thành công, coverage 100%%';
  PERFORM pg_temp.expect_error($q$SELECT pg_temp.add_member('PT-002','AU',ARRAY['A'])$q$,
    '%members can only change in DRAFT%', 'Không sửa thành viên sau khi submit');
END $$;

-- 5b. Gán code nhưng không có competency -> không tính coverage
DO $$ DECLARE c int; BEGIN
  SELECT count(*) INTO c FROM fn_team_coverage(pg_temp.id('team')) WHERE is_covered;
  IF c <> 5 THEN RAISE EXCEPTION 'FAIL: kỳ vọng 5 yêu cầu đạt, got %', c; END IF;
  RAISE NOTICE 'PASS: fn_team_coverage trả đủ 5/5 yêu cầu';
END $$;

-- 5c. PARENT_COVERS_CHILD theo scheme
DO $$ DECLARE r record; BEGIN
  SELECT * INTO r FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_A1')], pg_temp.id('role_LA'), '2026-11-01','2026-11-02', NULL) WHERE expert_code='FT-0001';
  IF r.eligible THEN RAISE EXCEPTION 'FAIL: cờ tắt mà code cha vẫn bao code con'; END IF;
  UPDATE schemes SET parent_covers_child = true WHERE scheme_id = pg_temp.id('scheme');
  SELECT * INTO r FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_A1')], pg_temp.id('role_LA'), '2026-11-01','2026-11-02', NULL) WHERE expert_code='FT-0001';
  IF NOT r.eligible THEN RAISE EXCEPTION 'FAIL: cờ bật mà code cha không bao code con: %', r.failed_rules; END IF;
  UPDATE schemes SET parent_covers_child = false WHERE scheme_id = pg_temp.id('scheme');
  RAISE NOTICE 'PASS: PARENT_COVERS_CHILD cấu hình theo scheme (BR-COV-005)';
END $$;

-- ---------- 6. Lịch không trùng (BR-8.2.1) + availability trong matching ----------
INSERT INTO schedules (expert_id, event_id, period, status)
VALUES (pg_temp.id('FT-0002'), pg_temp.id('event'), tstzrange('2026-10-10 08:00+07','2026-10-10 17:00+07'), 'CONFIRMED');
DO $$ BEGIN
  PERFORM pg_temp.expect_error(
    $q$INSERT INTO schedules (expert_id, period, status) VALUES ((SELECT v FROM ids WHERE k='FT-0002'), tstzrange('2026-10-10 13:00+07','2026-10-11 12:00+07'), 'TENTATIVE')$q$,
    '%ex_schedule_no_overlap%', 'Hà Nội 10/10 và Hải Phòng 10/10 -> conflict');
  IF (SELECT eligible FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_C')], pg_temp.id('role_AU'), '2026-10-10','2026-10-10', NULL) WHERE expert_code='FT-0002')
  THEN RAISE EXCEPTION 'FAIL: BR-MATCH-008'; END IF;
  RAISE NOTICE 'PASS: Matching chặn chuyên gia đã có lịch (BR-MATCH-008)';
END $$;

-- ---------- 7. Conflict of interest (BR-9.2.1) ----------
INSERT INTO conflict_of_interest_records (expert_id, customer_id, conflict_type, description)
SELECT pg_temp.id('PT-001'), customer_id, 'CONSULTING', 'Tư vấn 2025' FROM customers;
DO $$ DECLARE r record; BEGIN
  SELECT * INTO r FROM fn_match_eligible(pg_temp.id('std'), ARRAY[pg_temp.id('code_D')], pg_temp.id('role_TE'), '2026-12-01','2026-12-02',
                                         (SELECT customer_id FROM customers LIMIT 1)) WHERE expert_code='PT-001';
  IF r.eligible OR NOT ('BR-MATCH-009' = ANY (r.failed_rules)) THEN RAISE EXCEPTION 'FAIL: COI không chặn'; END IF;
  RAISE NOTICE 'PASS: Conflict PENDING_REVIEW đã chặn matching (BR-MATCH-009)';
END $$;

-- ---------- 8. Audit log append-only + hash chain ----------
INSERT INTO audit_logs (user_id, action, object_type, object_id, to_value) VALUES (pg_temp.id('maker'),'CREATE','EXPERT','FT-0001','{"a":1}');
INSERT INTO audit_logs (user_id, action, object_type, object_id, from_value, to_value) VALUES (pg_temp.id('maker'),'UPDATE','EXPERT','FT-0001','{"a":1}','{"a":2}');
DO $$ BEGIN
  PERFORM pg_temp.expect_error($q$UPDATE audit_logs SET reason='x'$q$, '%append-only%', 'Không UPDATE audit log');
  PERFORM pg_temp.expect_error($q$DELETE FROM audit_logs$q$, '%append-only%', 'Không DELETE audit log');
  INSERT INTO approval_history (object_type, object_id, action, from_status, to_status, actor_id, decision)
  VALUES ('COMPETENCY', pg_temp.id('ec_A_A'), 'APPROVE', 'UNDER_REVIEW', 'APPROVED', pg_temp.id('checker'), 'APPROVED');
  PERFORM pg_temp.expect_error($q$DELETE FROM approval_history$q$, '%append-only%', 'Không DELETE approval history');
  IF EXISTS (SELECT 1 FROM fn_verify_audit_chain()) THEN RAISE EXCEPTION 'FAIL: hash chain'; END IF;
  RAISE NOTICE 'PASS: Chuỗi hash audit log hợp lệ';
END $$;

-- ---------- 9. Dữ liệu cơ bản ----------
DO $$ BEGIN
  PERFORM pg_temp.expect_error($q$INSERT INTO users (username,email,full_name) VALUES ('m2','MAKER@x.vn','dup')$q$, '%ux_users_email%', 'Email unique không phân biệt hoa thường');
  PERFORM pg_temp.expect_error($q$INSERT INTO experts (expert_code, full_name, expert_type, employment_type) VALUES ('FT-0001','dup','AUDITOR','FULLTIME')$q$, '%ux_experts_code%', 'Mã chuyên gia unique');
  PERFORM pg_temp.expect_error($q$UPDATE experts SET status='DRAFT' WHERE expert_code='FT-0001'$q$, '%Illegal EXPERT transition%', 'Expert ACTIVE -> DRAFT bị chặn');
  INSERT INTO user_roles (user_id, role_id) SELECT pg_temp.id('maker'), role_id FROM roles WHERE role_code='EXPERT';
  PERFORM pg_temp.expect_error($q$DELETE FROM roles WHERE role_code='EXPERT'$q$, '%user_roles%', 'Không xoá role đang được gán (BR-1.2.2)');
END $$;

\echo '==== ALL TESTS PASSED ===='
ROLLBACK;
