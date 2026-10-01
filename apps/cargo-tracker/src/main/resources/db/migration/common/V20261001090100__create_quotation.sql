-- 見積りコンテキスト（quotation）
-- Bolt 1 では輸送要求のヘッダと版のうち、提出に使う列だけを作る。残りの列は使う Bolt のマイグレーションで足す。
CREATE SCHEMA IF NOT EXISTS quotation;

CREATE TABLE quotation.transport_request (
    id                 UUID        NOT NULL,
    shipper_company_id UUID        NOT NULL,
    status             VARCHAR(30) NOT NULL,
    current_version_no INTEGER     NOT NULL,
    version            BIGINT      NOT NULL,
    CONSTRAINT pk_transport_request PRIMARY KEY (id),
    CONSTRAINT ck_transport_request_status CHECK (status IN (
        'DRAFT', 'UNDER_REVIEW', 'QUOTING', 'QUOTED', 'ROUTING',
        'AWAITING_APPROVAL', 'READY_TO_BOOK', 'BOOKED', 'WITHDRAWN'))
);

-- 提出した版だけを INSERT し、更新しない（Q-INV-03）
CREATE TABLE quotation.transport_request_version (
    transport_request_id UUID                     NOT NULL,
    version_no           INTEGER                  NOT NULL,
    origin_unlocode      CHAR(5)                  NOT NULL,
    destination_unlocode CHAR(5)                  NOT NULL,
    submitted_by         UUID                     NOT NULL,
    submitted_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_transport_request_version PRIMARY KEY (transport_request_id, version_no),
    CONSTRAINT fk_transport_request_version_request
        FOREIGN KEY (transport_request_id) REFERENCES quotation.transport_request (id)
);

CREATE INDEX ix_transport_request_shipper_status ON quotation.transport_request (shipper_company_id, status);
