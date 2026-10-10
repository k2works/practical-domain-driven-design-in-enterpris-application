-- 主要実績（tracking.milestone）。Bolt 26b（US-12 AC1・AC2、T-INV-01・T-INV-02、データモデル `tracking`）。
-- この Bolt で使う列だけで作る。下書きの修正の変更者・変更時刻（revised_by・revised_at。W7）と顧客への表示（shown_to_customer。
-- US-11・US-13）は使う Bolt で足す。主要実績は削除・上書きしない（T-INV-01）。状態と下書きの内容は UPDATE するので追記専用ではなく、
-- 表の印 [no-delete] で PostgreSQL のアプリケーション利用者から DELETE の権限だけを外す（afterMigrate）。
CREATE TABLE tracking.milestone (
    tracking_number   VARCHAR(20)              NOT NULL,
    milestone_no      INTEGER                  NOT NULL,
    kind              VARCHAR(30)              NOT NULL,
    location_unlocode CHAR(5)                  NOT NULL,
    occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    source_kind       VARCHAR(30)              NOT NULL,
    source_ref        VARCHAR(200)             NOT NULL,
    acquired_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    state             VARCHAR(30)              NOT NULL,
    registered_by     UUID                     NOT NULL,
    registered_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_milestone PRIMARY KEY (tracking_number, milestone_no),
    CONSTRAINT fk_milestone_tracking_record FOREIGN KEY (tracking_number)
        REFERENCES tracking.tracking_record (tracking_number),
    -- T-INV-02: 同じ出典識別子（出典の種類と参照）の実績は 1 件。同時の登録の負けた側は競合にする
    CONSTRAINT uk_milestone_source UNIQUE (tracking_number, source_kind, source_ref),
    CONSTRAINT ck_milestone_no CHECK (milestone_no >= 1),
    CONSTRAINT ck_milestone_kind CHECK (kind IN (
        'PICKUP', 'RECEIPT_AT_ORIGIN', 'DEPARTURE', 'TRANSSHIPMENT', 'ARRIVAL', 'DELIVERY')),
    -- 出典の種類は航海の出典の種類（ck_voyage_source_kind）と同じ 4 値
    CONSTRAINT ck_milestone_source_kind CHECK (source_kind IN (
        'EXTERNAL_RECORD', 'FIELD_RECORD', 'INTERNAL_CHECK', 'MANUAL_ENTRY')),
    CONSTRAINT ck_milestone_state CHECK (state IN ('DRAFT', 'ADOPTED', 'UNDER_REVIEW', 'RETAINED_ONLY'))
);

COMMENT ON TABLE tracking.milestone IS '主要実績 [no-delete]';
COMMENT ON COLUMN tracking.milestone.tracking_number IS '追跡番号';
COMMENT ON COLUMN tracking.milestone.milestone_no IS '実績番号';
COMMENT ON COLUMN tracking.milestone.kind IS '種類';
COMMENT ON COLUMN tracking.milestone.location_unlocode IS '場所';
COMMENT ON COLUMN tracking.milestone.occurred_at IS '発生時刻';
COMMENT ON COLUMN tracking.milestone.source_kind IS '出典の種類';
COMMENT ON COLUMN tracking.milestone.source_ref IS '出典の参照';
COMMENT ON COLUMN tracking.milestone.acquired_at IS '出典の取得時刻';
COMMENT ON COLUMN tracking.milestone.state IS '状態';
COMMENT ON COLUMN tracking.milestone.registered_by IS '登録者';
COMMENT ON COLUMN tracking.milestone.registered_at IS '登録時刻';
