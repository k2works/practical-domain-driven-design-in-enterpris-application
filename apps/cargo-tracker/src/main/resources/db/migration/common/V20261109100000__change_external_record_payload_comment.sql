-- Bolt 39d4 の開始準備の決定 (3)。採用の判断で外部原本の基準（ハッシュとオブジェクトキー）を採用した payload に付け替えるので、列のコメントを事実に合わせる。
-- 列は変えない。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
COMMENT ON COLUMN external_data.external_record.payload_hash IS '採用中の payload のハッシュ（はじめは最初に受け取った payload。採用の判断で付け替える）';
COMMENT ON COLUMN external_data.external_record.payload_object_key IS '採用中の payload のオブジェクトキー（はじめは最初に受け取った payload。採用の判断で付け替える）';
