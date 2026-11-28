-- Bolt 39g4b の開始準備の決定 (6)（確認ポイント CP-6）。最後の区間の航海の到着予定（DE-12 から結んだ最後の揚地への到着予定）と、その知らせの公開の順（公開した時刻 →
-- 取得時刻 → 受信記録 ID。D-105）を足す。主要実績の UPDATE が書き、追跡の開始の INSERT は NULL を書く。到着予定は結べないときと受けていないときは NULL。順の鍵の 3 列は
-- そろって NULL かそろって NOT NULL で、到着予定に値があれば順の鍵も NOT NULL（CHECK）。既存の行は NULL のままで補正しない（次の DE-12 まで見込みは採用の実績だけで
-- 決まる。今と同じ）。DE-12 ごとに航海番号で予定区間を引くので scheduled_leg に索引を足す。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE tracking.tracking_record ADD COLUMN voyage_arrival_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tracking.tracking_record ADD COLUMN voyage_arrival_published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tracking.tracking_record ADD COLUMN voyage_arrival_acquired_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tracking.tracking_record ADD COLUMN voyage_arrival_source_ref VARCHAR(200);

ALTER TABLE tracking.tracking_record ADD CONSTRAINT ck_tracking_record_voyage_arrival CHECK (
    (voyage_arrival_published_at IS NULL AND voyage_arrival_acquired_at IS NULL AND voyage_arrival_source_ref IS NULL
        AND voyage_arrival_at IS NULL)
    OR (voyage_arrival_published_at IS NOT NULL AND voyage_arrival_acquired_at IS NOT NULL
        AND voyage_arrival_source_ref IS NOT NULL));

CREATE INDEX ix_scheduled_leg_voyage ON tracking.scheduled_leg (voyage_number);

COMMENT ON COLUMN tracking.tracking_record.voyage_arrival_at IS '航海の到着予定';
COMMENT ON COLUMN tracking.tracking_record.voyage_arrival_published_at IS '航海の到着予定の公開した時刻';
COMMENT ON COLUMN tracking.tracking_record.voyage_arrival_acquired_at IS '航海の到着予定の取得時刻';
COMMENT ON COLUMN tracking.tracking_record.voyage_arrival_source_ref IS '航海の到着予定の出典の参照';
