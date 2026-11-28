-- 業務番号と、提出に要る輸送条件（必要書類を除く）を足す（Bolt 4、D-4、D-10、Q-INV-01、Q-INV-12）。
-- ステージング・本番はまだなく、既存の行はローカルと Testcontainers の開発データだけなので、NOT NULL で足す。
-- H2 と PostgreSQL の共通の構文で書くため、ALTER TABLE は 1 列ずつ書く（ADR-007）。

-- 業務番号（TR-年-年ごとの連番）。画面と通知には業務番号だけを出す
ALTER TABLE quotation.transport_request ADD COLUMN request_number VARCHAR(20) NOT NULL;
ALTER TABLE quotation.transport_request ADD CONSTRAINT uk_transport_request_number UNIQUE (request_number);

-- 輸送要求版の輸送条件（追記専用の印は既に付いている）
ALTER TABLE quotation.transport_request_version ADD COLUMN consignee_company_id UUID NOT NULL;
ALTER TABLE quotation.transport_request_version ADD COLUMN arrival_deadline TIMESTAMP WITH TIME ZONE NOT NULL;
ALTER TABLE quotation.transport_request_version ADD COLUMN cargo_category VARCHAR(30) NOT NULL;
ALTER TABLE quotation.transport_request_version ADD COLUMN package_type VARCHAR(30) NOT NULL;
ALTER TABLE quotation.transport_request_version ADD COLUMN package_count INTEGER NOT NULL;
ALTER TABLE quotation.transport_request_version ADD COLUMN gross_weight_kg NUMERIC(12, 3) NOT NULL;
ALTER TABLE quotation.transport_request_version ADD COLUMN volume_m3 NUMERIC(12, 3) NOT NULL;
ALTER TABLE quotation.transport_request_version ADD CONSTRAINT ck_transport_request_version_cargo_category
    CHECK (cargo_category IN ('GENERAL', 'DANGEROUS', 'REEFER', 'OTHER_SPECIAL'));
ALTER TABLE quotation.transport_request_version ADD CONSTRAINT ck_transport_request_version_package_type
    CHECK (package_type IN ('PALLET', 'CARTON', 'CRATE', 'OTHER'));
ALTER TABLE quotation.transport_request_version ADD CONSTRAINT ck_transport_request_version_quantities
    CHECK (package_count >= 1 AND gross_weight_kg > 0 AND volume_m3 > 0);
ALTER TABLE quotation.transport_request_version ADD CONSTRAINT ck_transport_request_version_route
    CHECK (origin_unlocode <> destination_unlocode);

-- 業務番号の採番。年ごとに 1 行で、number_year は業務番号の年（year は H2 の予約語のため使わない）、last_no はその年に最後に振った連番（データモデル「業務番号の採番」）。
-- 行を更新するため追記専用ではない
CREATE TABLE quotation.transport_request_number_counter (
    number_year SMALLINT NOT NULL,
    last_no     INTEGER  NOT NULL,
    CONSTRAINT pk_transport_request_number_counter PRIMARY KEY (number_year),
    CONSTRAINT ck_transport_request_number_counter_last_no CHECK (last_no >= 0)
);

-- KPI 計測記録に、社内の一覧で示す業務番号の写しを足す。
-- Bolt 4 より前の DE-01 は業務番号を持たないため null を許す（イベントの進化の規則）
ALTER TABLE identity.kpi_observation ADD COLUMN transport_request_number VARCHAR(20);
