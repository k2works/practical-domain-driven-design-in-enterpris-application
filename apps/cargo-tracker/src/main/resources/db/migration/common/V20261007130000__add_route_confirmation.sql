-- 経路の確定（US-07 AC1・AC2、R-INV-03〜06）と経路版の一覧・案件一覧の並び（Bolt 17 レビュー D-64）。Bolt 19。
-- 確定の記録（選んだ候補・判断根拠・承認者・承認 commit 時刻）は、確定のときそろって値を持つ。確定の後の再設計要・旧版も持てる。
-- 参照情報版は確定した候補の区間（candidate_leg.info_version）から読み、写しの列は作らない。
ALTER TABLE routing.route_version ADD COLUMN candidates_found INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE routing.route_version ADD COLUMN selected_candidate_no INTEGER;
ALTER TABLE routing.route_version ADD COLUMN rationale VARCHAR(4000);
ALTER TABLE routing.route_version ADD COLUMN approved_by UUID;
ALTER TABLE routing.route_version ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;

-- Bolt 17 で算出した経路版は、示した候補の数を見つけた数にする（上限で切った数は残っていない）
UPDATE routing.route_version v
   SET candidates_found = (SELECT COUNT(*) FROM routing.route_candidate c
                            WHERE c.routing_case_id = v.routing_case_id
                              AND c.route_version_no = v.route_version_no);

ALTER TABLE routing.route_version ADD CONSTRAINT ck_route_version_found CHECK (candidates_found >= 0);
ALTER TABLE routing.route_version ADD CONSTRAINT ck_route_version_confirmed CHECK (
    ((selected_candidate_no IS NULL AND rationale IS NULL AND approved_by IS NULL AND approved_at IS NULL)
        OR (selected_candidate_no IS NOT NULL AND rationale IS NOT NULL AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL))
    AND (status <> 'CONFIRMED' OR selected_candidate_no IS NOT NULL)
    AND (status NOT IN ('DRAFT', 'CANDIDATES_PRESENTED', 'EXPERT_REVIEW') OR selected_candidate_no IS NULL));

ALTER TABLE routing.routing_case ADD COLUMN quotation_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE routing.routing_case ADD COLUMN created_by UUID;
ALTER TABLE routing.routing_case ADD COLUMN updated_by UUID;

COMMENT ON COLUMN routing.route_version.candidates_found IS '見つけた候補の数（上限で切る前。示さなかった候補の数を常に示すため）';
COMMENT ON COLUMN routing.route_version.selected_candidate_no IS '確定した候補番号（確定の記録。確定していなければ NULL）';
COMMENT ON COLUMN routing.route_version.rationale IS '判断根拠（確定の記録。前後の空白を除いて 1〜4,000 文字）';
COMMENT ON COLUMN routing.route_version.approved_by IS '承認者（経路設計者）の利用者 ID（確定の記録）';
COMMENT ON COLUMN routing.route_version.approved_at IS '承認 commit 時刻（確定の記録。確定の再検証の時刻）';
COMMENT ON COLUMN routing.routing_case.quotation_expires_at IS '見積有効期限（DE-16 の写し。案件一覧の並び。Bolt 19 より前の案件は NULL）';
COMMENT ON COLUMN routing.routing_case.created_by IS '詳細経路設計を依頼した荷主担当者の利用者 ID（DE-16 の写し）';
COMMENT ON COLUMN routing.routing_case.updated_by IS '最後に候補を算出・確定した経路設計者の利用者 ID';
