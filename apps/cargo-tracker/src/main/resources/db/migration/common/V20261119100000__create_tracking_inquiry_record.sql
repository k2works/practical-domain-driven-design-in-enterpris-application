-- Bolt 40-15（IR-INV-02、KPI-02 の分母）。照会記録。H2 と PostgreSQL の共通の構文で書く（ADR-007）。data_model の定義どおり。
-- 荷主企業・追跡番号・利用者は他のコンテキストの参照なので外部キーを持たない。有人案件への外部キーは、引き継いだときの受付番号だけ。
-- 追記専用の印は付けない（W9 の IR-INV-01 が、顧客 Web の照会が 24 時間以内の問い合わせで有人対応へ移ったとき escalated を更新するため）。
CREATE TABLE tracking.inquiry_record (
    id                 UUID                     NOT NULL,
    channel            VARCHAR(30)              NOT NULL,
    shipper_company_id UUID                     NOT NULL,
    tracking_number    VARCHAR(20),
    inquirer_kind      VARCHAR(30)              NOT NULL,
    inquirer_user_id   UUID,
    received_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    escalated          BOOLEAN                  NOT NULL,
    case_number        VARCHAR(20),
    recorded_by        UUID,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_inquiry_record PRIMARY KEY (id),
    CONSTRAINT fk_inquiry_record_case FOREIGN KEY (case_number) REFERENCES tracking.service_case (case_number),
    CONSTRAINT ck_inquiry_record_channel CHECK (channel IN ('WEB_SELF', 'WEB_INQUIRY', 'PHONE', 'EMAIL')),
    CONSTRAINT ck_inquiry_record_inquirer_kind CHECK (inquirer_kind IN ('SHIPPER', 'CONSIGNEE')),
    CONSTRAINT ck_inquiry_record_escalated CHECK (escalated = FALSE OR case_number IS NOT NULL)
);

CREATE INDEX ix_inquiry_record_shipper_received ON tracking.inquiry_record (shipper_company_id, received_at);

COMMENT ON TABLE tracking.inquiry_record IS '照会記録（経路ごとの照会 1 件。KPI-02 の分母）';
COMMENT ON COLUMN tracking.inquiry_record.id IS '照会記録 ID';
COMMENT ON COLUMN tracking.inquiry_record.channel IS '経路（顧客 Web の自己照会・Web の問い合わせ・電話・メール）';
COMMENT ON COLUMN tracking.inquiry_record.shipper_company_id IS '荷主企業 ID';
COMMENT ON COLUMN tracking.inquiry_record.tracking_number IS '追跡番号';
COMMENT ON COLUMN tracking.inquiry_record.inquirer_kind IS '照会者の種別（荷主・荷受人）';
COMMENT ON COLUMN tracking.inquiry_record.inquirer_user_id IS '照会者の利用者 ID（Web の照会のとき）';
COMMENT ON COLUMN tracking.inquiry_record.received_at IS '受付時刻';
COMMENT ON COLUMN tracking.inquiry_record.escalated IS '有人対応へ移ったか（真なら受付番号がある）';
COMMENT ON COLUMN tracking.inquiry_record.case_number IS '引き継いだ有人案件の受付番号';
COMMENT ON COLUMN tracking.inquiry_record.recorded_by IS '記録した利用者 ID（電話・メール）';
COMMENT ON COLUMN tracking.inquiry_record.created_at IS '記録した時刻';
