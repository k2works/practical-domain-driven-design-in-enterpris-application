-- 受信記録の値（Bolt 39d3 の開始準備の決定 (2)。Bolt 39d の開始準備の決定 (8) で決めた形）。
-- データ責任者が判断の画面で内容の違う原本の受け取った値とこの外部版の採用値を比べるため、外部原本を持つ受信記録（採用・重複・内容の違う
-- 原本の隔離）に、取込の値の対象のキーと値の要約を採用値（adopted_value）と同じ型と規則で残す。キーのない隔離（外部原本を持たない）は
-- 値を読めていないので持たない。2 列はそろって NULL かそろって値を持ち、外部原本を持つ受信記録にも必須にしない（Bolt 39d3 より前の行は
-- NULL のまま。受信記録は追記専用で更新しない）。H2 は広げない（要約は ASCII で UTF-16 の単位とコードポイントの数が同じ、キーは値の型の上限
-- （航海番号はコードポイントで 30 文字、追跡番号は 14 文字）で 100 単位に収まる。ADR-007）。
-- この外部版の採用値の照会は受信記録を外部原本 ID で絞るので、外部原本 ID の索引を足す（PostgreSQL は外部キーに索引を自動で作らない）。
-- 1 列・1 制約・1 索引ずつ書く（H2 の PostgreSQL 互換モードと PostgreSQL の両方で通る形。V20261104170000 の前例）。
ALTER TABLE external_data.received_record ADD COLUMN target_key VARCHAR(100);
ALTER TABLE external_data.received_record ADD COLUMN value_summary VARCHAR(4000);
ALTER TABLE external_data.received_record ADD CONSTRAINT ck_received_record_value
    CHECK ((target_key IS NULL) = (value_summary IS NULL)
        AND (external_record_id IS NOT NULL OR target_key IS NULL));
CREATE INDEX ix_received_record_external_record ON external_data.received_record (external_record_id);

COMMENT ON COLUMN external_data.received_record.target_key IS '対象のキー（航海は航海番号、主要実績は追跡番号。キーのない隔離と Bolt 39d3 より前の行は NULL）';
COMMENT ON COLUMN external_data.received_record.value_summary IS '値の要約（採用値と同じ規則の正規の文字列。キーのない隔離と Bolt 39d3 より前の行は NULL）';
