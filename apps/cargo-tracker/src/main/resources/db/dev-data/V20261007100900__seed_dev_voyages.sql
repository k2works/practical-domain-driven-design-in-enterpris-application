-- 開発環境だけの仮の航海データと接続時間規則（Bolt 17 の確認ポイント 6・7）。dev のときだけ Flyway の場所に足す。
-- 航海番号と港の組合せは架空（実在の船会社・船名を使わない）。東京（JPTYO）→ ロッテルダム（NLRTM）で、希望到着期限
-- 2026-11-02 09:00 JST（UTC 00:00）の見積依頼について、適合・期限と同時刻・接続不足・期限超過・規則のない港が見える組合せにする。
-- 外部原本の取込（US-14、W7）と、業務責任者の必要最小接続時間（DM-05、W6）が届いたら差し替える。

INSERT INTO routing.voyage (voyage_number, adopted_info_version, source_kind, source_ref, acquired_at, version, updated_at)
VALUES ('DEMO-101', 'PROVISIONAL-1', 'MANUAL_ENTRY', '仮の航海データ（Bolt 17）', TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00'),
       ('DEMO-201', 'PROVISIONAL-1', 'MANUAL_ENTRY', '仮の航海データ（Bolt 17）', TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00'),
       ('DEMO-202', 'PROVISIONAL-1', 'MANUAL_ENTRY', '仮の航海データ（Bolt 17）', TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00'),
       ('DEMO-301', 'PROVISIONAL-1', 'MANUAL_ENTRY', '仮の航海データ（Bolt 17）', TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00'),
       ('DEMO-302', 'PROVISIONAL-1', 'MANUAL_ENTRY', '仮の航海データ（Bolt 17）', TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00'),
       ('DEMO-303', 'PROVISIONAL-1', 'MANUAL_ENTRY', '仮の航海データ（Bolt 17）', TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00'),
       ('DEMO-304', 'PROVISIONAL-1', 'MANUAL_ENTRY', '仮の航海データ（Bolt 17）', TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00');

-- DEMO-101: 直行（適合）。DEMO-201: 東京 → シンガポール。DEMO-202: 東京 → 香港（香港には規則がない）
-- DEMO-301: シンガポールで接続 12 時間、到着が期限と同時刻（適合）。DEMO-302: 接続 4 時間（接続不足）。
-- DEMO-303: 到着が期限の 1 日 12 時間後（期限超過）。DEMO-304: 香港 → ロッテルダム（接続できない）
INSERT INTO routing.port_call (voyage_number, call_no, port_unlocode, arrival_at, departure_at)
VALUES ('DEMO-101', 1, 'JPTYO', NULL, TIMESTAMP WITH TIME ZONE '2026-10-12 00:00:00+00'),
       ('DEMO-101', 2, 'NLRTM', TIMESTAMP WITH TIME ZONE '2026-10-30 09:00:00+00', NULL),
       ('DEMO-201', 1, 'JPTYO', NULL, TIMESTAMP WITH TIME ZONE '2026-10-10 00:00:00+00'),
       ('DEMO-201', 2, 'SGSIN', TIMESTAMP WITH TIME ZONE '2026-10-20 00:00:00+00', NULL),
       ('DEMO-202', 1, 'JPTYO', NULL, TIMESTAMP WITH TIME ZONE '2026-10-11 00:00:00+00'),
       ('DEMO-202', 2, 'HKHKG', TIMESTAMP WITH TIME ZONE '2026-10-15 00:00:00+00', NULL),
       ('DEMO-301', 1, 'SGSIN', NULL, TIMESTAMP WITH TIME ZONE '2026-10-20 12:00:00+00'),
       ('DEMO-301', 2, 'NLRTM', TIMESTAMP WITH TIME ZONE '2026-11-02 00:00:00+00', NULL),
       ('DEMO-302', 1, 'SGSIN', NULL, TIMESTAMP WITH TIME ZONE '2026-10-20 04:00:00+00'),
       ('DEMO-302', 2, 'NLRTM', TIMESTAMP WITH TIME ZONE '2026-10-31 00:00:00+00', NULL),
       ('DEMO-303', 1, 'SGSIN', NULL, TIMESTAMP WITH TIME ZONE '2026-10-21 00:00:00+00'),
       ('DEMO-303', 2, 'NLRTM', TIMESTAMP WITH TIME ZONE '2026-11-03 12:00:00+00', NULL),
       ('DEMO-304', 1, 'HKHKG', NULL, TIMESTAMP WITH TIME ZONE '2026-10-16 00:00:00+00'),
       ('DEMO-304', 2, 'NLRTM', TIMESTAMP WITH TIME ZONE '2026-10-29 00:00:00+00', NULL);

-- シンガポールの必要最小接続時間（仮に 8 時間。S-06 の画面イメージの値。DM-05 で差し替える）
INSERT INTO routing.connection_rule (id, route_scope, port_unlocode, min_connection_minutes, valid_from, valid_to, version)
VALUES ('00000000-0000-0000-0000-000000001701', '*', 'SGSIN', 480, TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00', NULL, 0);
