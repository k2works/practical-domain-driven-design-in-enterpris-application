-- デモ環境のサンプルの業務データに、荷主が見積りと経路を承認した後に有効期限を過ぎた見積依頼（予約待ち）を足す（Bolt 35e。D-85）。
-- デモ環境で、S-02 の予約の確定待ちから再見積り・提示・荷主の再承認（C-04・C-17）・見積り 2 での本予約の確定（S-09）までを、
-- 経路設計と荷主の承認を毎回画面で進めずに確かめられるようにする（Bolt 35d の開始準備の決定 (4)・(6)）。経路は Bolt 24 の 0906 と同じ直行の候補（DEMO-101）を確定した経路版 1。
--   TR-2026-0908 荷主 A 予約待ち → RC-2026-0905（確定済み）。見積り 1 は承認済みで、有効期限は起動した日の前日の 0 時（UTC。起動した時点で失効）
-- dev のときだけ Flyway の場所に足す（application-dev.properties）。共通・ベンダーの場所に置かない（DevDataLocationTest で守る）。
-- イベントを処理し終えた後の状態を直接入れ、イベント発行記録は入れない（Bolt 18 と同じ）。日時は起動した日の 0 時（UTC）を基準にした相対。
-- 時刻の順は、見積りの集約が操作のときに守る順（提示 → 荷主の回答 → 荷主の承認）と経路設計の時計（回答 → 経路の確定 → 荷主の承認）に合わせる。
-- 読み込みでは検査されないので、順は DevSampleDataSmokeTest で守る。

INSERT INTO quotation.transport_request (id, shipper_company_id, status, current_version_no, version, request_number) VALUES
    ('00000000-0000-0000-0000-000000000908', '00000000-0000-0000-0000-000000000001', 'READY_TO_BOOK', 1, 0, 'TR-2026-0908');

INSERT INTO quotation.transport_request_version
    (transport_request_id, version_no, origin_unlocode, destination_unlocode, submitted_by, submitted_at, consignee_company_id,
     arrival_deadline, cargo_category, package_type, package_count, gross_weight_kg, volume_m3) VALUES
    ('00000000-0000-0000-0000-000000000908', 1, 'JPTYO', 'NLRTM', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '10' DAY + INTERVAL '1' HOUR, '00000000-0000-0000-0000-000000000201',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', 'PALLET', 12, 8400.000, 32.500);

INSERT INTO quotation.review_record (id, transport_request_id, version_no, decision, reviewer_id, rationale, missing_items, decided_at) VALUES
    ('00000000-0000-0000-0000-000000000918', '00000000-0000-0000-0000-000000000908', 1, 'APPROVED', '00000000-0000-0000-0000-000000000301', '契約条件と輸送条件を確認した（サンプル）', NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '10' DAY + INTERVAL '3' HOUR);

-- 見積り。詳細経路設計へ進むと回答し、確定した経路版 1 を割り当て、荷主が見積りと経路を承認した。その後に有効期限を過ぎた
INSERT INTO quotation.quotation
    (id, transport_request_id, quotation_no, transport_request_version_no, status, expires_at, total_amount, currency, route_policy_via,
     route_policy_departure_at, route_policy_arrival_at, internal_approved_by, internal_approved_at, presented_at,
     replaced_by_quotation_id, shipper_response, responded_by, responded_at, routing_case_number, route_version_no, route_confirmed_at,
     shipper_approved_by, shipper_approved_at, version) VALUES
    ('00000000-0000-0000-0000-000000000938', '00000000-0000-0000-0000-000000000908', 1, 1, 'APPROVED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY, 3000.00, 'USD', 'SGSIN',
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '3' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, '00000000-0000-0000-0000-000000000301', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '10' DAY + INTERVAL '5' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '10' DAY + INTERVAL '5' HOUR,
     NULL, 'PROCEED', '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '9' DAY + INTERVAL '1' HOUR, 'RC-2026-0905', 1, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '3' HOUR,
     '00000000-0000-0000-0000-000000000101', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '6' HOUR, 3);

INSERT INTO quotation.pricing_line (quotation_id, line_no, description, amount, currency, contract_reference) VALUES
    ('00000000-0000-0000-0000-000000000938', 1, '海上運賃', 2500.00, 'USD', '年間契約 2026（サンプル）'),
    ('00000000-0000-0000-0000-000000000938', 2, '港湾諸費用', 500.00, 'USD', NULL);

-- 割り当てた経路の区間の写し（経路設計の確定した候補 1 と同じ値。ADR-014）
INSERT INTO quotation.assigned_route_leg
    (quotation_id, leg_no, voyage_number, load_unlocode, discharge_unlocode, departure_at, arrival_at) VALUES
    ('00000000-0000-0000-0000-000000000938', 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '5' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR);

INSERT INTO identity.kpi_observation (transport_request_id, shipper_company_id, submitted_at, excluded, transport_request_number, first_presented_at) VALUES
    ('00000000-0000-0000-0000-000000000908', '00000000-0000-0000-0000-000000000001', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '10' DAY + INTERVAL '1' HOUR, FALSE, 'TR-2026-0908', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '10' DAY + INTERVAL '5' HOUR);

-- 業務番号の採番を、サンプルの続きから振る
UPDATE quotation.transport_request_number_counter SET last_no = 908 WHERE number_year = 2026;

-- 経路設計の案件。直行の候補 1 を確定した経路版 1（判断根拠と承認者は経路設計者）。見積有効期限は DE-16 の時点の写し（過ぎて見える）
INSERT INTO routing.routing_case
    (id, case_number, transport_request_id, transport_request_number, transport_request_version_no, quotation_id, route_policy_via,
     origin_unlocode, destination_unlocode, arrival_deadline, cargo_category, requested_at, confirmed_route_version_no, version,
     created_at, updated_at, quotation_expires_at, created_by, updated_by) VALUES
    ('00000000-0000-0000-0000-000000000945', 'RC-2026-0905', '00000000-0000-0000-0000-000000000908', 'TR-2026-0908', 1, '00000000-0000-0000-0000-000000000938', 'SGSIN',
     'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '26' DAY, 'GENERAL', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '9' DAY + INTERVAL '1' HOUR, NULL, 2,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '9' DAY + INTERVAL '1' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '3' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1' DAY, '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000302');

INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_evaluated_at, created_at, candidates_found) VALUES
    ('00000000-0000-0000-0000-000000000945', 1, 'CANDIDATES_PRESENTED', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '2' HOUR, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '9' DAY + INTERVAL '1' HOUR, 1);

INSERT INTO routing.route_candidate
    (routing_case_id, route_version_no, candidate_no, conforming, estimated_arrival_at, min_connection_slack_minutes, evaluated_at,
     oldest_info_acquired_at, info_insufficient) VALUES
    ('00000000-0000-0000-0000-000000000945', 1, 1, TRUE, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR, NULL, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '2' HOUR,
     CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '1' HOUR, FALSE);

INSERT INTO routing.candidate_leg
    (routing_case_id, route_version_no, candidate_no, leg_no, voyage_number, load_unlocode, discharge_unlocode, departure_at, arrival_at,
     cargo_cutoff_at, doc_cutoff_at, info_version, info_acquired_at, executed) VALUES
    ('00000000-0000-0000-0000-000000000945', 1, 1, 1, 'DEMO-101', 'JPTYO', 'NLRTM', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '5' DAY, CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) + INTERVAL '23' DAY + INTERVAL '9' HOUR,
     NULL, NULL, 'PROVISIONAL-1', CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '1' HOUR, FALSE);

-- 候補 1 を確定する。確定した経路版と選んだ候補は外部キーで参照するので、経路版・候補を入れた後に確定の値を入れる
UPDATE routing.route_version
   SET status = 'CONFIRMED',
       selected_candidate_no = 1,
       rationale = '直行で期限まで 3 日あり、積替えの遅れの心配がない。（サンプル）',
       approved_by = '00000000-0000-0000-0000-000000000302',
       approved_at = CAST(CURRENT_DATE AS TIMESTAMP WITH TIME ZONE) - INTERVAL '8' DAY + INTERVAL '3' HOUR
 WHERE routing_case_id = '00000000-0000-0000-0000-000000000945';

UPDATE routing.routing_case
   SET confirmed_route_version_no = 1
 WHERE id = '00000000-0000-0000-0000-000000000945';

-- 案件番号の採番を、サンプルの続きから振る
UPDATE routing.routing_case_number_counter SET last_no = 905 WHERE number_year = 2026;
