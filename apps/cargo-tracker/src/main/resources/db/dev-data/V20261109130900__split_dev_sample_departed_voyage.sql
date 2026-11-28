-- 見本 TR-2026-0911・0912（出発済みの見え方の見本）の区間を、見本専用の航海 DEMO-901 に分ける（Bolt 39e2 の決定 (5)。W7 の確認ポイント 14）。
-- V20261023200900 の注の「区間の航海は DEMO-101 のままにし」を改める（Flyway のチェックサムが変わるので、元の注は直さない）。
-- 確定の照合と確定した経路の評価が情報版と区間の予定の組で見るようになったので（R-INV-03。Bolt 39e2）、航海の予定と食い違う見本の区間を、その予定と一致する航海に指し直す。
-- 情報版・出典・取得時刻・版は DEMO-101 の行から写す（取得時刻は V20261021170900 で起動の時刻に寄せてあり、DevSampleDataSmokeTest が DEMO-% の全部でそろうことを確かめる）。
-- 寄港の時刻は RC-2026-0908 の候補の区間から写す（見本の写しと必ず一致する。文ごとに時刻を書かない）。JPTYO の出発は起動した日の前日なので、判定時刻より後に出発する
-- 寄港を持たず、候補の算出には入らない。dev のときだけ Flyway の場所に足す（application-dev.properties。DevDataLocationTest で守る）。

INSERT INTO routing.voyage (voyage_number, adopted_info_version, source_kind, source_ref, acquired_at, version, updated_at)
SELECT 'DEMO-901', adopted_info_version, source_kind, source_ref, acquired_at, version, updated_at
  FROM routing.voyage
 WHERE voyage_number = 'DEMO-101';

INSERT INTO routing.port_call (voyage_number, call_no, port_unlocode, arrival_at, departure_at)
SELECT 'DEMO-901', 1, l.load_unlocode, NULL, l.departure_at
  FROM routing.candidate_leg l
 WHERE l.routing_case_id = '00000000-0000-0000-0000-000000000950'
   AND l.route_version_no = 1 AND l.candidate_no = 1 AND l.leg_no = 1;

INSERT INTO routing.port_call (voyage_number, call_no, port_unlocode, arrival_at, departure_at)
SELECT 'DEMO-901', 2, l.discharge_unlocode, l.arrival_at, NULL
  FROM routing.candidate_leg l
 WHERE l.routing_case_id = '00000000-0000-0000-0000-000000000950'
   AND l.route_version_no = 1 AND l.candidate_no = 1 AND l.leg_no = 1;

UPDATE routing.candidate_leg
   SET voyage_number = 'DEMO-901'
 WHERE routing_case_id IN ('00000000-0000-0000-0000-000000000950', '00000000-0000-0000-0000-000000000951');

UPDATE quotation.assigned_route_leg
   SET voyage_number = 'DEMO-901'
 WHERE quotation_id IN ('00000000-0000-0000-0000-000000000948', '00000000-0000-0000-0000-000000000949');
