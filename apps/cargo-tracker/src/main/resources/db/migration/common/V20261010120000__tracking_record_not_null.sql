-- 追跡記録の予定の経路版と到着予定の 4 列を NOT NULL にする。Bolt 26c（Bolt 25 の P-9、Bolt 26b の P-10）。
-- 追跡の開始（Bolt 25）はこの 4 列に必ず値を入れ、集約も必ず持つので、表で守ってリポジトリの NULL の行の例外を消す。
-- 既存の行: デモ環境・開発・画面の層のテストは配備やテストのたびに空の DB から移行を流して作り、db/dev-data に追跡記録の行はない。
-- 経路版の 2 列そろいの CHECK（ck_tracking_record_route_version）は NOT NULL の後も正しく働くので変えない。
-- 1 列ずつ書く（H2 の PostgreSQL 互換モードと PostgreSQL の両方で通る形。V20261009100000 の前例）。
ALTER TABLE tracking.tracking_record ALTER COLUMN routing_case_number SET NOT NULL;
ALTER TABLE tracking.tracking_record ALTER COLUMN route_version_no SET NOT NULL;
ALTER TABLE tracking.tracking_record ALTER COLUMN original_eta SET NOT NULL;
ALTER TABLE tracking.tracking_record ALTER COLUMN latest_eta SET NOT NULL;
