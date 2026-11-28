-- アクセス・監査コンテキスト（identity）
-- Bolt 1 では KPI 計測記録の提出時刻だけを作る。残りの列と表は使う Bolt のマイグレーションで足す。
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.kpi_observation (
    transport_request_id UUID                     NOT NULL,
    shipper_company_id   UUID                     NOT NULL,
    submitted_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    excluded             BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_kpi_observation PRIMARY KEY (transport_request_id)
);

CREATE INDEX ix_kpi_observation_submitted_at ON identity.kpi_observation (submitted_at);
