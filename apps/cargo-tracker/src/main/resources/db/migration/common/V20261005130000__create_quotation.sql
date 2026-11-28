-- 見積りと料金明細（Bolt 10、US-03 AC1〜AC3、Q-INV-05・17・18、データモデル `quotation`・`pricing_line`）。
-- Bolt 10 は提示までに使う列と状態の値だけで作る。荷主の回答・経路版・荷主承認・置換の列と、ほかの状態の値は、
-- 使う Bolt のマイグレーションで足す（2026-10-05 に承認）。
-- 作成中（DRAFT）の見積りを保存できるよう、金額・期限・経路方針の列は NULL を許し、承認待ち以後の必須は CHECK で守る。
CREATE TABLE quotation.quotation (
    id                           UUID                     NOT NULL,
    transport_request_id         UUID                     NOT NULL,
    quotation_no                 INTEGER                  NOT NULL,
    transport_request_version_no INTEGER                  NOT NULL,
    status                       VARCHAR(30)              NOT NULL,
    expires_at                   TIMESTAMP WITH TIME ZONE,
    total_amount                 NUMERIC(15, 2),
    currency                     CHAR(3),
    route_policy_via             VARCHAR(200),
    route_policy_departure_at    TIMESTAMP WITH TIME ZONE,
    route_policy_arrival_at      TIMESTAMP WITH TIME ZONE,
    internal_approved_by         UUID,
    internal_approved_at         TIMESTAMP WITH TIME ZONE,
    presented_at                 TIMESTAMP WITH TIME ZONE,
    version                      BIGINT                   NOT NULL,
    CONSTRAINT pk_quotation PRIMARY KEY (id),
    CONSTRAINT uk_quotation_request_no UNIQUE (transport_request_id, quotation_no),
    CONSTRAINT fk_quotation_request_version
        FOREIGN KEY (transport_request_id, transport_request_version_no)
        REFERENCES quotation.transport_request_version (transport_request_id, version_no),
    CONSTRAINT ck_quotation_no CHECK (quotation_no >= 1),
    CONSTRAINT ck_quotation_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PRESENTED')),
    CONSTRAINT ck_quotation_currency CHECK (currency IN ('USD', 'EUR', 'JPY')),
    CONSTRAINT ck_quotation_total_amount CHECK (total_amount > 0),
    CONSTRAINT ck_quotation_calculated CHECK (status = 'DRAFT' OR (
        expires_at IS NOT NULL AND total_amount IS NOT NULL AND currency IS NOT NULL
        AND route_policy_via IS NOT NULL
        AND route_policy_departure_at IS NOT NULL AND route_policy_arrival_at IS NOT NULL)),
    CONSTRAINT ck_quotation_route_policy_dates CHECK (route_policy_arrival_at > route_policy_departure_at),
    CONSTRAINT ck_quotation_presented CHECK (status <> 'PRESENTED' OR (
        presented_at IS NOT NULL AND internal_approved_by IS NOT NULL AND internal_approved_at IS NOT NULL))
);

CREATE INDEX ix_quotation_request_status ON quotation.quotation (transport_request_id, status);

-- 料金根拠の明細。見積りに 1〜10 行（Q-INV-17）。通貨は見積りの通貨と同じ値（アプリケーションで守る）
CREATE TABLE quotation.pricing_line (
    quotation_id       UUID           NOT NULL,
    line_no            INTEGER        NOT NULL,
    description        VARCHAR(200)   NOT NULL,
    amount             NUMERIC(15, 2) NOT NULL,
    currency           CHAR(3)        NOT NULL,
    contract_reference VARCHAR(200),
    CONSTRAINT pk_pricing_line PRIMARY KEY (quotation_id, line_no),
    CONSTRAINT fk_pricing_line_quotation FOREIGN KEY (quotation_id) REFERENCES quotation.quotation (id),
    CONSTRAINT ck_pricing_line_no CHECK (line_no BETWEEN 1 AND 10),
    CONSTRAINT ck_pricing_line_amount CHECK (amount > 0),
    CONSTRAINT ck_pricing_line_currency CHECK (currency IN ('USD', 'EUR', 'JPY'))
);

-- 見積り
COMMENT ON TABLE quotation.quotation IS '見積り';
COMMENT ON COLUMN quotation.quotation.id IS '見積り ID';
COMMENT ON COLUMN quotation.quotation.transport_request_id IS '輸送要求 ID';
COMMENT ON COLUMN quotation.quotation.quotation_no IS '見積り番号';
COMMENT ON COLUMN quotation.quotation.transport_request_version_no IS '対象版番号';
COMMENT ON COLUMN quotation.quotation.status IS '状態';
COMMENT ON COLUMN quotation.quotation.expires_at IS '見積有効期限';
COMMENT ON COLUMN quotation.quotation.total_amount IS '合計';
COMMENT ON COLUMN quotation.quotation.currency IS '通貨';
COMMENT ON COLUMN quotation.quotation.route_policy_via IS '経路方針の主な経由地（UN/LOCODE のカンマ区切り）';
COMMENT ON COLUMN quotation.quotation.route_policy_departure_at IS '経路方針の概算の出発日時';
COMMENT ON COLUMN quotation.quotation.route_policy_arrival_at IS '経路方針の概算の到着日時';
COMMENT ON COLUMN quotation.quotation.internal_approved_by IS '社内承認者';
COMMENT ON COLUMN quotation.quotation.internal_approved_at IS '社内承認時刻';
COMMENT ON COLUMN quotation.quotation.presented_at IS '提示時刻（KPI-01 の終了）';
COMMENT ON COLUMN quotation.quotation.version IS '楽観ロックの版（業務の版番号とは別）';

-- 料金明細
COMMENT ON TABLE quotation.pricing_line IS '料金明細';
COMMENT ON COLUMN quotation.pricing_line.quotation_id IS '見積り ID';
COMMENT ON COLUMN quotation.pricing_line.line_no IS '明細の番号';
COMMENT ON COLUMN quotation.pricing_line.description IS '内容';
COMMENT ON COLUMN quotation.pricing_line.amount IS '金額';
COMMENT ON COLUMN quotation.pricing_line.currency IS '通貨';
COMMENT ON COLUMN quotation.pricing_line.contract_reference IS '参照した契約条件';
