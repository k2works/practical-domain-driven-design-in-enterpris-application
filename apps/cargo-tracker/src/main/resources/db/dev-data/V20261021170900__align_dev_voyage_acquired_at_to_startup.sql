-- 開発データの航海の取得時刻を起動の時刻に寄せる（Bolt 37b。W6 の確認ポイント 2）。
-- 航海の情報の鮮度（最終取得から 24 時間。US-06 AC4、D-89。Bolt 37b2）を判定すると、前日の 6 時の取得のままでは
-- 起動した日の 6 時を過ぎるとすべての候補が情報不足になる。起動のたびに入れ直すので、起動の時刻から 24 時間は足りる。
-- 見本の RC-2026-0902 の候補は、算出し直して同じになることを DevSampleDataSmokeTest が確かめるので、区間の取得時刻を
-- 航海から写し、判定時刻も同じ時刻に寄せる（取得が判定より後に見えないように）。時刻は文ごとに書かず、航海の値から写す。

UPDATE routing.voyage
   SET acquired_at = CURRENT_TIMESTAMP,
       updated_at = CURRENT_TIMESTAMP
 WHERE voyage_number LIKE 'DEMO-%';

UPDATE routing.candidate_leg l
   SET info_acquired_at = (SELECT v.acquired_at FROM routing.voyage v WHERE v.voyage_number = l.voyage_number)
 WHERE l.routing_case_id = '00000000-0000-0000-0000-000000000942';

UPDATE routing.route_candidate c
   SET oldest_info_acquired_at = (SELECT MIN(l.info_acquired_at)
                                    FROM routing.candidate_leg l
                                   WHERE l.routing_case_id = c.routing_case_id
                                     AND l.route_version_no = c.route_version_no
                                     AND l.candidate_no = c.candidate_no),
       evaluated_at = (SELECT MAX(v.acquired_at) FROM routing.voyage v WHERE v.voyage_number LIKE 'DEMO-%')
 WHERE c.routing_case_id = '00000000-0000-0000-0000-000000000942';

UPDATE routing.route_version
   SET candidates_evaluated_at = (SELECT MAX(v.acquired_at) FROM routing.voyage v WHERE v.voyage_number LIKE 'DEMO-%')
 WHERE routing_case_id = '00000000-0000-0000-0000-000000000942';
