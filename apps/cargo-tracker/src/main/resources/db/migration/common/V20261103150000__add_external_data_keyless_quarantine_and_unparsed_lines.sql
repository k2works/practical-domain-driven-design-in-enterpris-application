-- 形式の誤りの隔離（Bolt 39b2 の確認ポイント 1・2、ED-INV-02、UC-15 2a）。
-- (1) キーのない隔離: 形式の誤りの受信記録は外部原本と取得時刻を持たない。2 列を NULL 可にし、NULL を隔離だけに限る CHECK を足す。
-- (2) 取り込めなかった行: 取込の行に、引用符の閉じ忘れの行からファイルの終わりまで（またはファイルの誤りのときのデータ行のすべて）の
--     始まりと物理行の数を足す。取込の行を書くときに入れ、更新しない（追記専用のまま）。既存の行は既定で数 0・始まり NULL になる。
-- 取込全体の要確認は列で持たず、「取り込めなかった物理行の数が 0 より大きい、またはファイルの誤りの隔離の受信記録がある」と定める。
-- 1 列ずつ書く（H2 の PostgreSQL 互換モードと PostgreSQL の両方で通る形。V20261029101000 の前例）。
ALTER TABLE external_data.received_record ALTER COLUMN external_record_id DROP NOT NULL;
ALTER TABLE external_data.received_record ALTER COLUMN acquired_at DROP NOT NULL;
ALTER TABLE external_data.received_record ADD CONSTRAINT ck_received_record_keyless_quarantine
    CHECK (classification = 'QUARANTINED' OR (external_record_id IS NOT NULL AND acquired_at IS NOT NULL));

ALTER TABLE external_data.ingestion ADD COLUMN unparsed_from_line INTEGER;
ALTER TABLE external_data.ingestion ADD COLUMN unparsed_line_count INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE external_data.ingestion ADD CONSTRAINT ck_ingestion_unparsed_lines
    CHECK ((unparsed_line_count = 0 AND unparsed_from_line IS NULL)
        OR (unparsed_line_count > 0 AND unparsed_from_line IS NOT NULL));

COMMENT ON COLUMN external_data.received_record.external_record_id IS '外部原本 ID（隔離は NULL 可。形式の誤りの受信記録は外部原本を持たない）';
COMMENT ON COLUMN external_data.received_record.acquired_at IS '取得時刻（隔離は NULL 可。形式の誤りの受信記録は取得時刻を持たない）';
COMMENT ON COLUMN external_data.ingestion.received_count IS '受信件数（ファイルの物理データ行の数。ヘッダーとレコードの外の空行を除く）';
COMMENT ON COLUMN external_data.ingestion.unparsed_from_line IS '取り込めなかった行の始まり（物理行の番号。取り込めなかった行がなければ NULL）';
COMMENT ON COLUMN external_data.ingestion.unparsed_line_count IS '取り込めなかった物理行の数';
