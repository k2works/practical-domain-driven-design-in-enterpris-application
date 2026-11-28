-- デモ環境のサンプルの業務データに、割り当てた経路が再設計要になった見積依頼を 2 件足す（US-07 AC5。Bolt 37e3b）。
-- 本番に評価の入口がまだない（W7）ので、経路設計の評価と DE-06 の伝達を処理し終えた後の状態を直接入れ、デモ環境で C-04・C-17・S-02・
-- S-04・S-09・S-06・S-07 の再設計要の見え方（Bolt 37e3）を確かめられるようにする（Bolt 37e3b の開始準備の決定 (1)）。経路は 0906 と同じ
-- 直行の候補（DEMO-101）を確定した経路版 1 で、確定の後に期限超過で再設計要にした。
--   TR-2026-0909 荷主 A 荷主承認待ち → RC-2026-0906（再設計要）。見積り 1 は荷主承認待ちで印あり（荷主は承認できない）。有効期限は起動した日の 6 日後
--   TR-2026-0910 荷主 A 予約待ち   → RC-2026-0907（再設計要）。見積り 1 は荷主の承認の後に印あり（本予約に使えない）。有効期限は起動した日の 7 日後
-- 最初の区間の出発は起動した日の 5 日後にして、出発済みの判定（D-88。Bolt 37e4）と混ぜない。
-- dev のときだけ Flyway の場所に足す（application-dev.properties）。共通・ベンダーの場所に置かない（DevDataLocationTest で守る）。
-- イベントを処理し終えた後の状態を直接入れ、イベント発行記録は入れない（Bolt 18 と同じ）。日時は起動した日の 0 時（UTC）を基準にした相対。
-- 時刻の順（提示 → 荷主の回答 → 経路の確定 →（荷主の承認）→ 再設計要 → 起動 → 出発 → 有効期限）は読み込みでは検査されないので、
-- DevSampleDataSmokeTest で守る。案件の confirmed_route_version_no は V20261022100000 で消えたので入れない（0908 の見本と違う）。

INSERT INTO quotation.transport_request (id, shipper_company_id, status, current_version_no, version, request_number) VALUES
    ('00000000-0000-0000-0000-000000000909', '00000000-0000-0000-0000-000000000001', 'AWAITING_APPROVAL', 1, 0, 'TR-2026-0909'),
    ('00000000-0000-0000-0000-000000000910', '00000000-0000-0000-0000-000000000001', 'READY_TO_BOOK', 1, 0, 'TR-2026-0910');

INSERT INTO quotation.transport_request_version
    (transport_request_id, version_no, origin_unlocode, destination_unlocode, submitted_by, submitted_at, consignee_company_id,
     arrival_deadline, cargo_category, package_type, package_count, gross_weight_kg, volume_m3) VALUES
    ('00000000-0000-0000-0000-000000000909', 1, 'JPTYO', 'NLRTM', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '1' HOUR, '00000000-0000-0000-0000-000000000201',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', 'PALLET', 12, 8400.000, 32.500),
    ('00000000-0000-0000-0000-000000000910', 1, 'JPTYO', 'NLRTM', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '2' HOUR, '00000000-0000-0000-0000-000000000201',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', 'PALLET', 12, 8400.000, 32.500);

INSERT INTO quotation.review_record (id, transport_request_id, version_no, decision, reviewer_id, rationale, missing_items, decided_at) VALUES
    ('00000000-0000-0000-0000-000000000919', '00000000-0000-0000-0000-000000000909', 1, 'APPROVED', '00000000-0000-0000-0000-000000000301', '契約条件と輸送条件を確認した（サンプル）', NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '3' HOUR),
    ('00000000-0000-0000-0000-000000000920', '00000000-0000-0000-0000-000000000910', 1, 'APPROVED', '00000000-0000-0000-0000-000000000301', '契約条件と輸送条件を確認した（サンプル）', NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '4' HOUR);

-- 見積り。詳細経路設計へ進むと回答し、確定した経路版 1 を割り当てた。0910 は荷主が承認した。その後に経路版が再設計要になり、印が付いた
INSERT INTO quotation.quotation
    (id, transport_request_id, quotation_no, transport_request_version_no, status, expires_at, total_amount, currency, route_policy_via,
     route_policy_departure_at, route_policy_arrival_at, internal_approved_by, internal_approved_at, presented_at,
     replaced_by_quotation_id, shipper_response, responded_by, responded_at, routing_case_number, route_version_no, route_confirmed_at,
     shipper_approved_by, shipper_approved_at, route_redesign_required_at, version) VALUES
    ('00000000-0000-0000-0000-000000000939', '00000000-0000-0000-0000-000000000909', 1, 1, 'AWAITING_SHIPPER_APPROVAL', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '6' DAY, 3000.00, 'USD', 'SGSIN',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '3' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, '00000000-0000-0000-0000-000000000301', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '5' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '5' HOUR,
     NULL, 'PROCEED', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '1' HOUR, 'RC-2026-0906', 1, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '3' HOUR,
     NULL, NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY + INTERVAL '1' HOUR, 3),
    ('00000000-0000-0000-0000-000000000940', '00000000-0000-0000-0000-000000000910', 1, 1, 'APPROVED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '7' DAY, 3000.00, 'USD', 'SGSIN',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '3' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, '00000000-0000-0000-0000-000000000301', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '6' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '6' HOUR,
     NULL, 'PROCEED', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '2' HOUR, 'RC-2026-0907', 1, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '4' HOUR,
     '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '3' DAY + INTERVAL '6' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY + INTERVAL '2' HOUR, 4);

INSERT INTO quotation.pricing_line (quotation_id, line_no, description, amount, currency, contract_reference) VALUES
    ('00000000-0000-0000-0000-000000000939', 1, '海上運賃', 2500.00, 'USD', '年間契約 2026（サンプル）'),
    ('00000000-0000-0000-0000-000000000939', 2, '港湾諸費用', 500.00, 'USD', NULL),
    ('00000000-0000-0000-0000-000000000940', 1, '海上運賃', 2500.00, 'USD', '年間契約 2026（サンプル）'),
    ('00000000-0000-0000-0000-000000000940', 2, '港湾諸費用', 500.00, 'USD', NULL);

-- 割り当てた経路の区間の写し（経路設計の確定した候補 1 と同じ値。ADR-014）
INSERT INTO quotation.assigned_route_leg
    (quotation_id, leg_no, voyage_number, load_unlocode, discharge_unlocode, departure_at, arrival_at) VALUES
    ('00000000-0000-0000-0000-000000000939', 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '5' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR),
    ('00000000-0000-0000-0000-000000000940', 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '5' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR);

INSERT INTO identity.kpi_observation (transport_request_id, shipper_company_id, submitted_at, excluded, transport_request_number, first_presented_at) VALUES
    ('00000000-0000-0000-0000-000000000909', '00000000-0000-0000-0000-000000000001', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '1' HOUR, FALSE, 'TR-2026-0909', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '5' HOUR),
    ('00000000-0000-0000-0000-000000000910', '00000000-0000-0000-0000-000000000001', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '2' HOUR, FALSE, 'TR-2026-0910', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '6' HOUR);

-- 業務番号の採番を、サンプルの続きから振る
UPDATE quotation.transport_request_number_counter SET last_no = 910 WHERE number_year = 2026;

-- 経路設計の案件。直行の候補 1 を確定した経路版 1 を、確定の後に評価で再設計要にした。見積有効期限は DE-16 の時点の写し
INSERT INTO routing.routing_case
    (id, case_number, transport_request_id, transport_request_number, transport_request_version_no, quotation_id, route_policy_via,
     origin_unlocode, destination_unlocode, arrival_deadline, cargo_category, requested_at, version,
     created_at, updated_at, quotation_expires_at, created_by, updated_by) VALUES
    ('00000000-0000-0000-0000-000000000946', 'RC-2026-0906', '00000000-0000-0000-0000-000000000909', 'TR-2026-0909', 1, '00000000-0000-0000-0000-000000000939', 'SGSIN',
     'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '1' HOUR, 3,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '1' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY + INTERVAL '1' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '6' DAY, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000302'),
    ('00000000-0000-0000-0000-000000000947', 'RC-2026-0907', '00000000-0000-0000-0000-000000000910', 'TR-2026-0910', 1, '00000000-0000-0000-0000-000000000940', 'SGSIN',
     'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '2' HOUR, 3,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '2' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY + INTERVAL '2' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '7' DAY, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000302');

INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_evaluated_at, created_at, candidates_found) VALUES
    ('00000000-0000-0000-0000-000000000946', 1, 'CANDIDATES_PRESENTED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '2' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '1' HOUR, 1),
    ('00000000-0000-0000-0000-000000000947', 1, 'CANDIDATES_PRESENTED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '3' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '2' HOUR, 1);

INSERT INTO routing.route_candidate
    (routing_case_id, route_version_no, candidate_no, conforming, estimated_arrival_at, min_connection_slack_minutes, evaluated_at,
     oldest_info_acquired_at, info_insufficient) VALUES
    ('00000000-0000-0000-0000-000000000946', 1, 1, TRUE, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR, NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '2' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '1' HOUR, FALSE),
    ('00000000-0000-0000-0000-000000000947', 1, 1, TRUE, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR, NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '3' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '1' HOUR, FALSE);

INSERT INTO routing.candidate_leg
    (routing_case_id, route_version_no, candidate_no, leg_no, voyage_number, load_unlocode, discharge_unlocode, departure_at, arrival_at,
     cargo_cutoff_at, doc_cutoff_at, info_version, info_acquired_at, executed) VALUES
    ('00000000-0000-0000-0000-000000000946', 1, 1, 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '5' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR,
     NULL, NULL, 'PROVISIONAL-1', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '1' HOUR, FALSE),
    ('00000000-0000-0000-0000-000000000947', 1, 1, 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '5' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR,
     NULL, NULL, 'PROVISIONAL-1', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '1' HOUR, FALSE);

-- 候補 1 の確定の記録と、確定の後の評価の再設計要の記録（期限超過）。確定の記録と選んだ候補は外部キーで参照するので、経路版・候補を
-- 入れた後に入れる。再設計要は確定の記録と再設計要の記録をそろえて持つ（CHECK 制約。V20261022100000）。時刻は見積りの印と同じ（DE-06 の伝達）
UPDATE routing.route_version
   SET status = 'REDESIGN_REQUIRED',
       selected_candidate_no = 1,
       rationale = '直行で期限まで 3 日あり、積替えの遅れの心配がない。（サンプル）',
       approved_by = '00000000-0000-0000-0000-000000000302',
       approved_at = CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '3' HOUR,
       redesign_required_at = CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY + INTERVAL '1' HOUR,
       redesign_reasons = 'DEADLINE_EXCEEDED'
 WHERE routing_case_id = '00000000-0000-0000-0000-000000000946';

UPDATE routing.route_version
   SET status = 'REDESIGN_REQUIRED',
       selected_candidate_no = 1,
       rationale = '直行で期限まで 3 日あり、積替えの遅れの心配がない。（サンプル）',
       approved_by = '00000000-0000-0000-0000-000000000302',
       approved_at = CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '4' HOUR,
       redesign_required_at = CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY + INTERVAL '2' HOUR,
       redesign_reasons = 'DEADLINE_EXCEEDED'
 WHERE routing_case_id = '00000000-0000-0000-0000-000000000947';

-- 案件番号の採番を、サンプルの続きから振る
UPDATE routing.routing_case_number_counter SET last_no = 907 WHERE number_year = 2026;
