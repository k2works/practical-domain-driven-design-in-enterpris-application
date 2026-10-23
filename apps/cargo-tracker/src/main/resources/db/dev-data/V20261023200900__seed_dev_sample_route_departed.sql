-- デモ環境のサンプルの業務データに、割り当てた経路の最初の区間が出発した見積依頼を 2 件足す（US-07 AC5、D-88。Bolt 37e4b）。
-- デモ環境で C-04・C-17・S-02・S-04・S-09 の出発済みの見え方（Bolt 37e4）を確かめられるようにする（Bolt 37e4b の開始準備の決定 (1)）。
-- 経路は 0906 と同じ直行の候補（DEMO-101）を確定した経路版 1 で、確定（と荷主の承認）の後に最初の区間が出発した。
--   TR-2026-0911 荷主 A 荷主承認待ち → RC-2026-0908（確定済み）。見積り 1 は荷主承認待ち（荷主は承認できない）。有効期限は起動した日の 6 日後
--   TR-2026-0912 荷主 A 予約待ち   → RC-2026-0909（確定済み）。見積り 1 は荷主の承認の後に出発（本予約に使えない）。有効期限は起動した日の 8 日後
-- 区間の航海は DEMO-101 のままにし、割り当てた区間と候補の区間の写しの出発だけを起動した日の前日にする（航海の見本の予定とは食い違う。
-- W7 の評価の入口をつなぐときに見直す。Bolt 37e4b の開始準備の決定 (2)、リリース計画の技術リスク）。経路方針の出発も出発より前にする。
-- dev のときだけ Flyway の場所に足す（application-dev.properties）。共通・ベンダーの場所に置かない（DevDataLocationTest で守る）。
-- イベントを処理し終えた後の状態を直接入れ、イベント発行記録は入れない（Bolt 18 と同じ）。日時は起動した日の 0 時（UTC）を基準にした相対。
-- 時刻の順（提示 → 荷主の回答 → 候補の算出 → 経路の確定 →（荷主の承認）→ 出発 → 起動 → 有効期限）は読み込みでは検査されないので、
-- DevSampleDataSmokeTest で守る。列の並びは V20261023180900 に従う（案件の confirmed_route_version_no は V20261022100000 で消えた）。

INSERT INTO quotation.transport_request (id, shipper_company_id, status, current_version_no, version, request_number) VALUES
    ('00000000-0000-0000-0000-000000000911', '00000000-0000-0000-0000-000000000001', 'AWAITING_APPROVAL', 1, 0, 'TR-2026-0911'),
    ('00000000-0000-0000-0000-000000000912', '00000000-0000-0000-0000-000000000001', 'READY_TO_BOOK', 1, 0, 'TR-2026-0912');

INSERT INTO quotation.transport_request_version
    (transport_request_id, version_no, origin_unlocode, destination_unlocode, submitted_by, submitted_at, consignee_company_id,
     arrival_deadline, cargo_category, package_type, package_count, gross_weight_kg, volume_m3) VALUES
    ('00000000-0000-0000-0000-000000000911', 1, 'JPTYO', 'NLRTM', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '7' HOUR, '00000000-0000-0000-0000-000000000201',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', 'PALLET', 12, 8400.000, 32.500),
    ('00000000-0000-0000-0000-000000000912', 1, 'JPTYO', 'NLRTM', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '10' HOUR, '00000000-0000-0000-0000-000000000201',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', 'PALLET', 12, 8400.000, 32.500);

INSERT INTO quotation.review_record (id, transport_request_id, version_no, decision, reviewer_id, rationale, missing_items, decided_at) VALUES
    ('00000000-0000-0000-0000-000000000921', '00000000-0000-0000-0000-000000000911', 1, 'APPROVED', '00000000-0000-0000-0000-000000000301', '契約条件と輸送条件を確認した（サンプル）', NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '8' HOUR),
    ('00000000-0000-0000-0000-000000000922', '00000000-0000-0000-0000-000000000912', 1, 'APPROVED', '00000000-0000-0000-0000-000000000301', '契約条件と輸送条件を確認した（サンプル）', NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '11' HOUR);

-- 見積り。詳細経路設計へ進むと回答し、確定した経路版 1 を割り当てた。0912 は荷主が承認した。その後に最初の区間が出発した（印は持たない）
INSERT INTO quotation.quotation
    (id, transport_request_id, quotation_no, transport_request_version_no, status, expires_at, total_amount, currency, route_policy_via,
     route_policy_departure_at, route_policy_arrival_at, internal_approved_by, internal_approved_at, presented_at,
     replaced_by_quotation_id, shipper_response, responded_by, responded_at, routing_case_number, route_version_no, route_confirmed_at,
     shipper_approved_by, shipper_approved_at, route_redesign_required_at, version) VALUES
    ('00000000-0000-0000-0000-000000000948', '00000000-0000-0000-0000-000000000911', 1, 1, 'AWAITING_SHIPPER_APPROVAL', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '6' DAY, 3000.00, 'USD', 'SGSIN',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '2' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY, '00000000-0000-0000-0000-000000000301', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '9' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '9' HOUR,
     NULL, 'PROCEED', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '3' HOUR, 'RC-2026-0908', 1, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '6' HOUR,
     NULL, NULL, NULL, 2),
    ('00000000-0000-0000-0000-000000000949', '00000000-0000-0000-0000-000000000912', 1, 1, 'APPROVED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '8' DAY, 3000.00, 'USD', 'SGSIN',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '2' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY, '00000000-0000-0000-0000-000000000301', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '12' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '12' HOUR,
     NULL, 'PROCEED', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '4' HOUR, 'RC-2026-0909', 1, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '8' HOUR,
     '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '3' DAY + INTERVAL '8' HOUR, NULL, 3);

INSERT INTO quotation.pricing_line (quotation_id, line_no, description, amount, currency, contract_reference) VALUES
    ('00000000-0000-0000-0000-000000000948', 1, '海上運賃', 2500.00, 'USD', '年間契約 2026（サンプル）'),
    ('00000000-0000-0000-0000-000000000948', 2, '港湾諸費用', 500.00, 'USD', NULL),
    ('00000000-0000-0000-0000-000000000949', 1, '海上運賃', 2500.00, 'USD', '年間契約 2026（サンプル）'),
    ('00000000-0000-0000-0000-000000000949', 2, '港湾諸費用', 500.00, 'USD', NULL);

-- 割り当てた経路の区間の写し（経路設計の確定した候補 1 と同じ値。ADR-014）。出発は起動した日の前日
INSERT INTO quotation.assigned_route_leg
    (quotation_id, leg_no, voyage_number, load_unlocode, discharge_unlocode, departure_at, arrival_at) VALUES
    ('00000000-0000-0000-0000-000000000948', 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY + INTERVAL '9' HOUR),
    ('00000000-0000-0000-0000-000000000949', 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY + INTERVAL '9' HOUR);

INSERT INTO identity.kpi_observation (transport_request_id, shipper_company_id, submitted_at, excluded, transport_request_number, first_presented_at) VALUES
    ('00000000-0000-0000-0000-000000000911', '00000000-0000-0000-0000-000000000001', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '7' HOUR, FALSE, 'TR-2026-0911', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '9' HOUR),
    ('00000000-0000-0000-0000-000000000912', '00000000-0000-0000-0000-000000000001', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '10' HOUR, FALSE, 'TR-2026-0912', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '6' DAY + INTERVAL '12' HOUR);

-- 業務番号の採番を、サンプルの続きから振る
UPDATE quotation.transport_request_number_counter SET last_no = 912 WHERE number_year = 2026;

-- 経路設計の案件。直行の候補 1 を確定した経路版 1。見積有効期限は DE-16 の時点の写し
INSERT INTO routing.routing_case
    (id, case_number, transport_request_id, transport_request_number, transport_request_version_no, quotation_id, route_policy_via,
     origin_unlocode, destination_unlocode, arrival_deadline, cargo_category, requested_at, version,
     created_at, updated_at, quotation_expires_at, created_by, updated_by) VALUES
    ('00000000-0000-0000-0000-000000000950', 'RC-2026-0908', '00000000-0000-0000-0000-000000000911', 'TR-2026-0911', 1, '00000000-0000-0000-0000-000000000948', 'SGSIN',
     'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '3' HOUR, 2,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '3' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '6' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '6' DAY, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000302'),
    ('00000000-0000-0000-0000-000000000951', 'RC-2026-0909', '00000000-0000-0000-0000-000000000912', 'TR-2026-0912', 1, '00000000-0000-0000-0000-000000000949', 'SGSIN',
     'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '4' HOUR, 2,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '4' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '8' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '8' DAY, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000302');

INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_evaluated_at, created_at, candidates_found) VALUES
    ('00000000-0000-0000-0000-000000000950', 1, 'CANDIDATES_PRESENTED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '5' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '3' HOUR, 1),
    ('00000000-0000-0000-0000-000000000951', 1, 'CANDIDATES_PRESENTED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '7' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '5' DAY + INTERVAL '4' HOUR, 1);

INSERT INTO routing.route_candidate
    (routing_case_id, route_version_no, candidate_no, conforming, estimated_arrival_at, min_connection_slack_minutes, evaluated_at,
     oldest_info_acquired_at, info_insufficient) VALUES
    ('00000000-0000-0000-0000-000000000950', 1, 1, TRUE, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY + INTERVAL '9' HOUR, NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '5' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '4' HOUR, FALSE),
    ('00000000-0000-0000-0000-000000000951', 1, 1, TRUE, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY + INTERVAL '9' HOUR, NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '7' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '6' HOUR, FALSE);

INSERT INTO routing.candidate_leg
    (routing_case_id, route_version_no, candidate_no, leg_no, voyage_number, load_unlocode, discharge_unlocode, departure_at, arrival_at,
     cargo_cutoff_at, doc_cutoff_at, info_version, info_acquired_at, executed) VALUES
    ('00000000-0000-0000-0000-000000000950', 1, 1, 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY + INTERVAL '9' HOUR,
     NULL, NULL, 'PROVISIONAL-1', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '4' HOUR, FALSE),
    ('00000000-0000-0000-0000-000000000951', 1, 1, 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '17' DAY + INTERVAL '9' HOUR,
     NULL, NULL, 'PROVISIONAL-1', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '6' HOUR, FALSE);

-- 候補 1 の確定の記録。確定の記録と選んだ候補は外部キーで参照するので、経路版・候補を入れた後に入れる。時刻は見積りの写しと同じ
UPDATE routing.route_version
   SET status = 'CONFIRMED',
       selected_candidate_no = 1,
       rationale = '直行で期限まで 8 日あり、積替えの遅れの心配がない。（サンプル）',
       approved_by = '00000000-0000-0000-0000-000000000302',
       approved_at = CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '6' HOUR
 WHERE routing_case_id = '00000000-0000-0000-0000-000000000950';

UPDATE routing.route_version
   SET status = 'CONFIRMED',
       selected_candidate_no = 1,
       rationale = '直行で期限まで 8 日あり、積替えの遅れの心配がない。（サンプル）',
       approved_by = '00000000-0000-0000-0000-000000000302',
       approved_at = CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '4' DAY + INTERVAL '8' HOUR
 WHERE routing_case_id = '00000000-0000-0000-0000-000000000951';

-- 案件番号の採番を、サンプルの続きから振る
UPDATE routing.routing_case_number_counter SET last_no = 909 WHERE number_year = 2026;
