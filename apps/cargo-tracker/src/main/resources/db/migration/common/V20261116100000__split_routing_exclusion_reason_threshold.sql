-- 除外理由の閾値（threshold）を区分ごとの 6 列に分ける（Bolt 39i。【前の決定の変更】(32)(34)）。
-- 今の threshold は区分ごとの文字列（期限は ISO 8601 の時刻、接続不足は「港 期間」、接続できないは港、情報不足は「航海番号 期間」、
-- 貨物種別は種別の名前）を詰めて、読むときに区切りを解いていた。区分と列の組を CHECK で守る形に改める。
-- 手順は H2 と PostgreSQL の共通の構文で書く（ADR-007）。追加列はすべて NULL を許す（既存の行に足せるように）。
-- 理由の行は消さない（理由を消すと除外の候補が適合に変わる）。壊れた行（区切りがない、閾値が NULL）は、移し替えの後に区分の列が
-- NULL のままになり、CHECK の追加で失敗して止まる（既定の値で通さない）。PostgreSQL は DDL もトランザクションなので巻き戻る。
-- ステージング・本番が W10 までないため、足して移して消すを 1 つにする例外（W10 の後に同じ変更をするときは 3 段にする）。

ALTER TABLE routing.exclusion_reason ADD COLUMN threshold_deadline TIMESTAMP WITH TIME ZONE;
ALTER TABLE routing.exclusion_reason ADD COLUMN threshold_port_unlocode CHAR(5);
ALTER TABLE routing.exclusion_reason ADD COLUMN threshold_required_connection VARCHAR(30);
ALTER TABLE routing.exclusion_reason ADD COLUMN threshold_voyage_number VARCHAR(30);
ALTER TABLE routing.exclusion_reason ADD COLUMN threshold_freshness_tolerance VARCHAR(30);
ALTER TABLE routing.exclusion_reason ADD COLUMN threshold_cargo_category VARCHAR(30);

UPDATE routing.exclusion_reason
   SET threshold_deadline = CAST(threshold AS TIMESTAMP WITH TIME ZONE)
 WHERE reason_code = 'DEADLINE_EXCEEDED';

UPDATE routing.exclusion_reason
   SET threshold_port_unlocode = SUBSTRING(threshold FROM 1 FOR 5),
       threshold_required_connection = SUBSTRING(threshold FROM 7)
 WHERE reason_code = 'CONNECTION_TOO_SHORT' AND POSITION(' ' IN threshold) > 0;

UPDATE routing.exclusion_reason
   SET threshold_port_unlocode = threshold
 WHERE reason_code = 'NOT_CONNECTABLE';

-- 航海番号は空白を含み得るので、最後の空白で分ける（Bolt 37b2）
UPDATE routing.exclusion_reason
   SET threshold_voyage_number = REGEXP_REPLACE(threshold, ' [^ ]*$', ''),
       threshold_freshness_tolerance = REGEXP_REPLACE(threshold, '^.* ', '')
 WHERE reason_code = 'INFO_INSUFFICIENT' AND POSITION(' ' IN threshold) > 0;

UPDATE routing.exclusion_reason
   SET threshold_cargo_category = threshold
 WHERE reason_code = 'CARGO_NOT_SUPPORTED';

ALTER TABLE routing.exclusion_reason ADD CONSTRAINT ck_exclusion_reason_threshold CHECK (
    (reason_code = 'DEADLINE_EXCEEDED' AND threshold_deadline IS NOT NULL AND threshold_port_unlocode IS NULL
        AND threshold_required_connection IS NULL AND threshold_voyage_number IS NULL
        AND threshold_freshness_tolerance IS NULL AND threshold_cargo_category IS NULL)
    OR (reason_code = 'CONNECTION_TOO_SHORT' AND threshold_port_unlocode IS NOT NULL
        AND threshold_required_connection IS NOT NULL
        AND threshold_deadline IS NULL AND threshold_voyage_number IS NULL
        AND threshold_freshness_tolerance IS NULL AND threshold_cargo_category IS NULL)
    OR (reason_code = 'NOT_CONNECTABLE' AND threshold_port_unlocode IS NOT NULL AND threshold_deadline IS NULL
        AND threshold_required_connection IS NULL AND threshold_voyage_number IS NULL
        AND threshold_freshness_tolerance IS NULL AND threshold_cargo_category IS NULL)
    OR (reason_code = 'INFO_INSUFFICIENT' AND threshold_voyage_number IS NOT NULL
        AND threshold_freshness_tolerance IS NOT NULL
        AND threshold_deadline IS NULL AND threshold_port_unlocode IS NULL
        AND threshold_required_connection IS NULL AND threshold_cargo_category IS NULL)
    OR (reason_code = 'CARGO_NOT_SUPPORTED' AND threshold_cargo_category IS NOT NULL AND threshold_deadline IS NULL
        AND threshold_port_unlocode IS NULL AND threshold_required_connection IS NULL
        AND threshold_voyage_number IS NULL AND threshold_freshness_tolerance IS NULL));

ALTER TABLE routing.exclusion_reason DROP COLUMN threshold;

COMMENT ON COLUMN routing.exclusion_reason.threshold_deadline IS '閾値: 希望到着期限（期限超過だけ）';
COMMENT ON COLUMN routing.exclusion_reason.threshold_port_unlocode IS '閾値: 規則の港（接続不足・接続できない）';
COMMENT ON COLUMN routing.exclusion_reason.threshold_required_connection IS '閾値: 必要最小接続時間（ISO 8601 の期間。接続不足だけ）';
COMMENT ON COLUMN routing.exclusion_reason.threshold_voyage_number IS '閾値: 古い航海の航海番号（情報不足だけ）';
COMMENT ON COLUMN routing.exclusion_reason.threshold_freshness_tolerance IS '閾値: 情報鮮度の許容時間（ISO 8601 の期間。情報不足だけ）';
COMMENT ON COLUMN routing.exclusion_reason.threshold_cargo_category IS '閾値: 案件の貨物種別の名前（貨物種別だけ）';
