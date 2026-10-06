-- =====================================================================
-- V11: Guard bổ sung + hàm pre-filter cho Matching Engine
-- Matching Engine chính chạy ở ems-rule-engine (Java, deterministic, BR-5.2.1).
-- Hàm SQL dưới đây là bước PRE-FILTER ở DB để tránh load toàn bộ chuyên gia
-- (BR-5.1.2, NFR-P-01): tính sẵn cờ cho 9 hard rule BR-MATCH-001..009, Java
-- dùng các cờ này + WARNING rules để dựng Rule Explanation.
-- =====================================================================

-- Expert status state machine
CREATE OR REPLACE FUNCTION trg_expert_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM fn_assert_transition('EXPERT', OLD.status, NEW.status);
    RETURN NEW;
END $$;
CREATE TRIGGER expert_guard BEFORE UPDATE OF status ON experts
    FOR EACH ROW EXECUTE FUNCTION trg_expert_guard();

-- Chỉ được sửa thành viên khi đoàn ở DRAFT (tránh "lách" coverage sau khi đã submit)
CREATE OR REPLACE FUNCTION trg_team_member_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_status text; v_team uuid;
BEGIN
    IF TG_TABLE_NAME = 'team_members' THEN
        v_team := COALESCE(NEW.team_id, OLD.team_id);
    ELSE
        SELECT team_id INTO v_team FROM team_members
         WHERE team_member_id = COALESCE(NEW.team_member_id, OLD.team_member_id);
    END IF;
    SELECT status INTO v_status FROM assessment_teams WHERE team_id = v_team;
    IF v_status IS NOT NULL AND v_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Team % is %, members can only change in DRAFT', v_team, v_status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER team_member_guard      BEFORE INSERT OR UPDATE OR DELETE ON team_members
    FOR EACH ROW EXECUTE FUNCTION trg_team_member_guard();
CREATE TRIGGER team_member_code_guard BEFORE INSERT OR UPDATE OR DELETE ON team_member_codes
    FOR EACH ROW EXECUTE FUNCTION trg_team_member_guard();

-- ---------------------------------------------------------------------
-- Matching pre-filter
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_match_candidates(
    p_standard_id uuid,
    p_code_ids    uuid[],          -- code yêu cầu (có thể rỗng nếu chỉ cần role)
    p_role_id     uuid,            -- NULL = mọi role tính coverage
    p_from        date,
    p_to          date,
    p_customer_id uuid             -- NULL nếu chưa có KH
)
RETURNS TABLE (
    expert_id        uuid,
    expert_code      varchar,
    full_name        varchar,
    r001_has_competency boolean,   -- BR-MATCH-001
    r002_approved       boolean,   -- BR-MATCH-002
    r003_code_match     boolean,   -- BR-MATCH-003
    r004_standard_match boolean,   -- BR-MATCH-004 (luôn true trong tập ứng viên, giữ để giải thích)
    r005_role_match     boolean,   -- BR-MATCH-005
    r006_not_suspended  boolean,   -- BR-MATCH-006 (expert status + restriction)
    r007_not_expired    boolean,   -- BR-MATCH-007
    r008_available      boolean,   -- BR-MATCH-008
    r009_no_conflict    boolean,   -- BR-MATCH-009
    covered_code_ids    uuid[],
    min_effective_to    date,
    eligible            boolean
)
LANGUAGE sql STABLE AS $$
WITH params AS (
    SELECT sc.parent_covers_child,
           tstzrange(p_from::timestamptz, (p_to + 1)::timestamptz, '[)') AS win
    FROM standards s JOIN schemes sc ON sc.scheme_id = s.scheme_id
    WHERE s.standard_id = p_standard_id
),
req_codes AS (
    SELECT c.code_id, c.path FROM codes c WHERE c.code_id = ANY (COALESCE(p_code_ids, '{}'))
),
comp AS (   -- mọi competency của chuyên gia cho Standard này (chưa bị thay thế/thu hồi)
    SELECT ec.expert_id, ec.status, ec.effective_from, ec.effective_to,
           cd.assessment_role_id, cd.code_id, dc.path
    FROM expert_competencies ec
    JOIN competency_definitions cd ON cd.competency_definition_id = ec.competency_definition_id
    LEFT JOIN codes dc ON dc.code_id = cd.code_id
    WHERE cd.standard_id = p_standard_id
      AND ec.status NOT IN ('SUPERSEDED','REJECTED')
),
cand AS (SELECT DISTINCT comp.expert_id FROM comp),
role_ok AS (
    SELECT * FROM comp
    WHERE p_role_id IS NULL OR comp.assessment_role_id = p_role_id
),
valid AS (  -- APPROVED và còn hiệu lực trọn khoảng đánh giá
    SELECT * FROM role_ok
    WHERE status = 'APPROVED'
      AND effective_from <= p_from
      AND (effective_to IS NULL OR effective_to >= p_to)
),
covered AS (
    SELECT v.expert_id, array_agg(DISTINCT rc.code_id) AS code_ids
    FROM valid v
    JOIN req_codes rc ON (v.code_id = rc.code_id
                          OR ((SELECT parent_covers_child FROM params) AND v.path IS NOT NULL AND rc.path LIKE v.path || '%'))
    GROUP BY v.expert_id
),
code_any AS (   -- có competency (bất kỳ trạng thái) khớp code -> để giải thích "code match"
    SELECT DISTINCT r.expert_id
    FROM role_ok r
    JOIN req_codes rc ON (r.code_id = rc.code_id
                          OR ((SELECT parent_covers_child FROM params) AND r.path IS NOT NULL AND rc.path LIKE r.path || '%'))
)
SELECT e.expert_id, e.expert_code, e.full_name,
       true                                                           AS r001,
       EXISTS (SELECT 1 FROM role_ok r WHERE r.expert_id = e.expert_id AND r.status = 'APPROVED') AS r002,
       (cardinality(COALESCE(p_code_ids,'{}')) = 0 OR EXISTS (SELECT 1 FROM code_any ca WHERE ca.expert_id = e.expert_id)) AS r003,
       true                                                           AS r004,
       EXISTS (SELECT 1 FROM role_ok r WHERE r.expert_id = e.expert_id) AS r005,
       (e.status = 'ACTIVE' AND NOT EXISTS (
            SELECT 1 FROM restrictions rs
            WHERE rs.expert_id = e.expert_id AND rs.status = 'ACTIVE'
              AND rs.restriction_type IN ('SUSPEND','CUSTOMER_BAN')
              AND daterange(rs.from_date, rs.to_date, '[]') && daterange(p_from, p_to, '[]')
              AND (rs.standard_id IS NULL OR rs.standard_id = p_standard_id)
              AND (rs.scheme_id   IS NULL OR rs.scheme_id = (SELECT scheme_id FROM standards WHERE standard_id = p_standard_id))
              AND (rs.assessment_role_id IS NULL OR p_role_id IS NULL OR rs.assessment_role_id = p_role_id)
              AND (rs.code_id IS NULL OR rs.code_id = ANY (COALESCE(p_code_ids,'{}')))
              AND (rs.customer_id IS NULL OR rs.customer_id = p_customer_id)
       ))                                                             AS r006,
       EXISTS (SELECT 1 FROM valid v WHERE v.expert_id = e.expert_id) AS r007,
       (NOT EXISTS (SELECT 1 FROM schedules s
                     WHERE s.expert_id = e.expert_id AND s.status IN ('TENTATIVE','CONFIRMED')
                       AND s.period && (SELECT win FROM params))
        AND NOT EXISTS (SELECT 1 FROM expert_availabilities a
                     WHERE a.expert_id = e.expert_id AND a.availability_type <> 'TENTATIVE'
                       AND a.period && (SELECT win FROM params)))      AS r008,
       (p_customer_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM conflict_of_interest_records ci
            WHERE ci.expert_id = e.expert_id AND ci.customer_id = p_customer_id
              AND ci.result IN ('PENDING_REVIEW','CONFIRMED')
              AND (ci.valid_to IS NULL OR ci.valid_to >= p_from)))     AS r009,
       COALESCE(cv.code_ids, '{}')                                     AS covered_code_ids,
       (SELECT min(v.effective_to) FROM valid v WHERE v.expert_id = e.expert_id) AS min_effective_to,
       false                                                           AS eligible
FROM cand
JOIN experts e ON e.expert_id = cand.expert_id AND e.deleted_at IS NULL
LEFT JOIN covered cv ON cv.expert_id = e.expert_id
$$;

-- Wrapper tính cột eligible = AND toàn bộ hard rule + có đóng góp coverage
CREATE OR REPLACE FUNCTION fn_match_eligible(
    p_standard_id uuid, p_code_ids uuid[], p_role_id uuid, p_from date, p_to date, p_customer_id uuid)
RETURNS TABLE (expert_id uuid, expert_code varchar, full_name varchar, eligible boolean,
               failed_rules text[], covered_code_ids uuid[], min_effective_to date)
LANGUAGE sql STABLE AS $$
SELECT m.expert_id, m.expert_code, m.full_name,
       cardinality(f.failed) = 0
         AND (cardinality(COALESCE(p_code_ids,'{}')) = 0 OR cardinality(m.covered_code_ids) > 0) AS eligible,
       f.failed, m.covered_code_ids, m.min_effective_to
FROM fn_match_candidates(p_standard_id, p_code_ids, p_role_id, p_from, p_to, p_customer_id) m
CROSS JOIN LATERAL (
    SELECT array_remove(ARRAY[
        CASE WHEN NOT m.r001_has_competency THEN 'BR-MATCH-001' END,
        CASE WHEN NOT m.r002_approved       THEN 'BR-MATCH-002' END,
        CASE WHEN NOT m.r003_code_match     THEN 'BR-MATCH-003' END,
        CASE WHEN NOT m.r004_standard_match THEN 'BR-MATCH-004' END,
        CASE WHEN NOT m.r005_role_match     THEN 'BR-MATCH-005' END,
        CASE WHEN NOT m.r006_not_suspended  THEN 'BR-MATCH-006' END,
        CASE WHEN NOT m.r007_not_expired    THEN 'BR-MATCH-007' END,
        CASE WHEN NOT m.r008_available      THEN 'BR-MATCH-008' END,
        CASE WHEN NOT m.r009_no_conflict    THEN 'BR-MATCH-009' END
    ], NULL) AS failed
) f
$$;
