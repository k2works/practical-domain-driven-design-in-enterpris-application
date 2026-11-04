-- 受信記録の行の範囲（Bolt 39c3 の開始準備の決定 (5)・(6)。Bolt 39c2 の開始準備の決定 (6) で決めた形）。
-- 隔離の理由（注記）をファイルの行の順に読むため、受信記録に物理行の範囲の始まりと終わりを足す。形式の正しい行と行の誤りは行の範囲、
-- ファイルの終わりまで取り込めなかった範囲は始まりからファイルの終わりの物理行まで、ファイルの誤り（ファイル全体の原本を指す）は NULL。
-- 既存の行は NULL のまま（受信記録は追記専用で更新しない）。索引は足さない（ix_received_record_ingestion で 1 つの取込に絞ってから並べる）。
-- 1 列・1 制約ずつ書く（H2 の PostgreSQL 互換モードと PostgreSQL の両方で通る形。V20261103150000 の前例）。
ALTER TABLE external_data.received_record ADD COLUMN first_line_number INTEGER;
ALTER TABLE external_data.received_record ADD COLUMN last_line_number INTEGER;
ALTER TABLE external_data.received_record ADD CONSTRAINT ck_received_record_line_range
    CHECK ((first_line_number IS NULL) = (last_line_number IS NULL)
        AND (first_line_number IS NULL OR (first_line_number >= 1 AND first_line_number <= last_line_number)));

COMMENT ON COLUMN external_data.received_record.first_line_number IS '行の範囲の始まり（物理行の番号。ファイルの誤りと Bolt 39c3 より前の行は NULL）';
COMMENT ON COLUMN external_data.received_record.last_line_number IS '行の範囲の終わり（物理行の番号。ファイルの誤りと Bolt 39c3 より前の行は NULL）';
