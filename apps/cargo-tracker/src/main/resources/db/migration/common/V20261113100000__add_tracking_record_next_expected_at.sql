-- Bolt 39g4 の開始準備の決定 (6)（確認ポイント CP-6）。次の予定の実績の予定時刻を足す（FRS-02 の鮮度超過の判定は読むときにこの値と判定の時刻で行い、判定は保存しない）。
-- 追跡の開始の INSERT と主要実績の UPDATE が書く。完了後と次の予定の実績がないときは NULL。既存の行は NULL のままで補正しない（次に採用の実績が
-- 変わるまで鮮度超過と判定されない。配備の前に件数を確かめる）。索引は足さない（S-11 はこの列で絞らない。W10）。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE tracking.tracking_record ADD COLUMN next_expected_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN tracking.tracking_record.next_expected_at IS '次の予定の実績の予定時刻';
