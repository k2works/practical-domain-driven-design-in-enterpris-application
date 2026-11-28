-- Bolt 39g の開始準備の決定 (3)。DE-13 から受けた実績の外部の識別の 3 列と、実績にしなかった DE-13 の拒否の記録を足す。
-- 外部原本の実績の行は今ない（画面で選べない）ので埋め直さない。情報版の列は持たない（順に使わず、受信記録 ID から外部データの原本にたどれる）。
-- H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE tracking.milestone ADD COLUMN data_source_id UUID;
ALTER TABLE tracking.milestone ADD COLUMN external_id VARCHAR(200);
ALTER TABLE tracking.milestone ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tracking.milestone ADD CONSTRAINT ck_milestone_external_origin CHECK (
    (source_kind = 'EXTERNAL_RECORD' AND data_source_id IS NOT NULL AND external_id IS NOT NULL AND published_at IS NOT NULL)
    OR (source_kind <> 'EXTERNAL_RECORD' AND data_source_id IS NULL AND external_id IS NULL AND published_at IS NULL)
);

COMMENT ON COLUMN tracking.milestone.data_source_id IS '情報源 ID（出典が外部原本の実績だけが持つ）';
COMMENT ON COLUMN tracking.milestone.external_id IS '外部 ID（出典が外部原本の実績だけが持つ。提供元の中でだけ一意）';
COMMENT ON COLUMN tracking.milestone.published_at IS '外部データが採用値を公開した時刻（出典が外部原本の実績だけが持つ。順の規則に使う）';

-- 知らない追跡番号も写すので、追跡記録への外部キーを持たない。同じ出典（種類と参照）は 1 件だけ（同時の負けた側は既存とみなす）。
CREATE TABLE tracking.rejected_milestone (
    id                UUID                     NOT NULL,
    tracking_number   VARCHAR(20)              NOT NULL,
    kind              VARCHAR(30)              NOT NULL,
    location_unlocode CHAR(5)                  NOT NULL,
    occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    source_kind       VARCHAR(30)              NOT NULL,
    source_ref        VARCHAR(200)             NOT NULL,
    acquired_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    data_source_id    UUID                     NOT NULL,
    external_id       VARCHAR(200)             NOT NULL,
    reason            VARCHAR(30)              NOT NULL,
    recorded_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_rejected_milestone PRIMARY KEY (id),
    CONSTRAINT uk_rejected_milestone_source UNIQUE (source_kind, source_ref),
    CONSTRAINT ck_rejected_milestone_kind CHECK (kind IN (
        'PICKUP', 'RECEIPT_AT_ORIGIN', 'DEPARTURE', 'TRANSSHIPMENT', 'ARRIVAL', 'DELIVERY')),
    CONSTRAINT ck_rejected_milestone_source_kind CHECK (source_kind IN (
        'EXTERNAL_RECORD', 'FIELD_RECORD', 'INTERNAL_CHECK', 'MANUAL_ENTRY')),
    CONSTRAINT ck_rejected_milestone_reason CHECK (reason IN ('UNKNOWN_TRACKING_NUMBER', 'BOOKING_CANCELLED'))
);

COMMENT ON TABLE tracking.rejected_milestone IS '拒否した主要実績 [append-only]';
COMMENT ON COLUMN tracking.rejected_milestone.id IS '拒否の記録 ID';
COMMENT ON COLUMN tracking.rejected_milestone.tracking_number IS '追跡番号（知らない追跡番号もそのまま写す）';
COMMENT ON COLUMN tracking.rejected_milestone.kind IS '実績の種類';
COMMENT ON COLUMN tracking.rejected_milestone.location_unlocode IS '場所（UN/LOCODE）';
COMMENT ON COLUMN tracking.rejected_milestone.occurred_at IS '発生時刻';
COMMENT ON COLUMN tracking.rejected_milestone.source_kind IS '出典の種類';
COMMENT ON COLUMN tracking.rejected_milestone.source_ref IS '出典の参照（DE-13 は受信記録 ID）';
COMMENT ON COLUMN tracking.rejected_milestone.acquired_at IS '出典の取得時刻';
COMMENT ON COLUMN tracking.rejected_milestone.data_source_id IS '情報源 ID';
COMMENT ON COLUMN tracking.rejected_milestone.external_id IS '外部 ID';
COMMENT ON COLUMN tracking.rejected_milestone.reason IS '拒否の理由（UNKNOWN_TRACKING_NUMBER: 知らない追跡番号、BOOKING_CANCELLED: 取消済みの予約）';
COMMENT ON COLUMN tracking.rejected_milestone.recorded_at IS '記録した時刻';
