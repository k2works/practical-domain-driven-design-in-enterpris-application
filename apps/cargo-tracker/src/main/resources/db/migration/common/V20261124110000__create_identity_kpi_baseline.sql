-- KPI 基準値（US-21 AC5、KPI-INV-02、PV-01。W9 の Bolt 44-2）。パイロット開始前 4 週間の KPI-01・KPI-02 を、算出方法と登録者とともに残す。
-- 追記専用: 更新・削除はしない。訂正は supersedes_id で前の記録を指す新しい行にする（PostgreSQL ではアプリケーション利用者から UPDATE・DELETE を外す。
-- 表の印 [append-only] が選ぶ）。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
-- 登録者は外部キーを持たない（追記専用の記録は利用者の状態に左右されない。audit_record と同じ）。
-- 値は KPI-01 が分（value_p50 が中央値、value_p90 が 90 パーセンタイル）、KPI-02 が自己照会率のパーセント（value_p50 に入れ、value_p90 は持たない）。
CREATE TABLE identity.kpi_baseline (
    id            UUID                     NOT NULL,
    kpi           VARCHAR(30)              NOT NULL,
    period_from   DATE                     NOT NULL,
    period_to     DATE                     NOT NULL,
    value_p50     NUMERIC(12, 2)           NOT NULL,
    value_p90     NUMERIC(12, 2),
    method        VARCHAR(4000)            NOT NULL,
    registered_by UUID                     NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    supersedes_id UUID,
    CONSTRAINT pk_kpi_baseline PRIMARY KEY (id),
    CONSTRAINT fk_kpi_baseline_supersedes FOREIGN KEY (supersedes_id) REFERENCES identity.kpi_baseline (id),
    CONSTRAINT ck_kpi_baseline_kpi CHECK (kpi IN ('KPI-01', 'KPI-02')),
    CONSTRAINT ck_kpi_baseline_period CHECK (period_to >= period_from),
    CONSTRAINT ck_kpi_baseline_value_p50 CHECK (value_p50 >= 0),
    CONSTRAINT ck_kpi_baseline_value_p90 CHECK (value_p90 IS NULL OR (kpi = 'KPI-01' AND value_p90 >= value_p50))
);

CREATE INDEX ix_kpi_baseline_kpi_registered ON identity.kpi_baseline (kpi, registered_at);

COMMENT ON TABLE identity.kpi_baseline IS 'KPI 基準値（パイロット開始前 4 週間の値） [append-only]';
COMMENT ON COLUMN identity.kpi_baseline.id IS '基準値 ID';
COMMENT ON COLUMN identity.kpi_baseline.kpi IS 'KPI の種類（KPI-01 経路提示リードタイム、KPI-02 自己照会率）';
COMMENT ON COLUMN identity.kpi_baseline.period_from IS '基準値の期間の開始日（両端を含む。28 日）';
COMMENT ON COLUMN identity.kpi_baseline.period_to IS '基準値の期間の終了日';
COMMENT ON COLUMN identity.kpi_baseline.value_p50 IS '中央値（KPI-01 は分、KPI-02 は自己照会率のパーセント）';
COMMENT ON COLUMN identity.kpi_baseline.value_p90 IS '90 パーセンタイル（KPI-01 の分。KPI-02 は持たない）';
COMMENT ON COLUMN identity.kpi_baseline.method IS '算出方法（現行業務の記録からどう算出したか）';
COMMENT ON COLUMN identity.kpi_baseline.registered_by IS '登録者の利用者 ID';
COMMENT ON COLUMN identity.kpi_baseline.registered_at IS '登録時刻';
COMMENT ON COLUMN identity.kpi_baseline.supersedes_id IS '訂正した前の基準値 ID（初めての登録なら NULL）';
