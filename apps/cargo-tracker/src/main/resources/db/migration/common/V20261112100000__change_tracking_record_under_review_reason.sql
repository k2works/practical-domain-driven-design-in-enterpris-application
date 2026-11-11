-- Bolt 39gc の開始準備の決定 (11)（確認ポイント CP-3）。確認中の理由を区分値にする（データモデルの区分値の規約: VARCHAR(30) と CHECK）。
-- Bolt 25 で VARCHAR(1000)・NULL 可で作った列で、書く経路がなかったので値のある行はない（現在状態が確認中の行もない。ステージング・本番は配備の前に確かめる）。
-- 現在状態が確認中のときだけ理由に値を持ち、それ以外は NULL（集約の DerivedStatus と同じ規則）。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE tracking.tracking_record ALTER COLUMN under_review_reason SET DATA TYPE VARCHAR(30);
ALTER TABLE tracking.tracking_record ADD CONSTRAINT ck_tracking_record_under_review_reason CHECK (
    under_review_reason IN ('OUT_OF_SEQUENCE')
);
ALTER TABLE tracking.tracking_record ADD CONSTRAINT ck_tracking_record_under_review CHECK (
    (current_status = 'UNDER_REVIEW' AND under_review_reason IS NOT NULL)
    OR (current_status <> 'UNDER_REVIEW' AND under_review_reason IS NULL)
);

COMMENT ON COLUMN tracking.tracking_record.under_review_reason IS '確認中の理由（区分値。現在状態が確認中のときだけ持つ）';
