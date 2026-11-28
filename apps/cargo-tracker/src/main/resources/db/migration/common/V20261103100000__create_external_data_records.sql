-- 外部データ（external_data）。Bolt 39a3（外部原本の取込、データモデル `external_data`）。
-- 情報源・取込・外部原本・受信記録・受信記録の判断・採用値の 6 表を新設する。表の順は外部キーの向き。
-- 外部キーに ON DELETE は付けない（保持期限の削除は所有者の運用タスクで子の表から消す）。
-- 外部原本は情報源への外部キーを張らない（冪等性キーは情報源の承認の状態と独立し、出典は受信記録 → 取込 → 情報源でたどれる）。
-- 受信記録・受信記録の判断・採用値は追記専用で、UPDATE・DELETE の権限は afterMigrate で外す（表の定義の印）。
-- 外部原本・取込・情報源には印を付けない（外部原本は Bolt 39d の再送の扱いで最初のハッシュを変えうる、情報源は承認の状態を変える、
-- 取込は Bolt 39b で受け取った件数の書き方を決める）。

CREATE TABLE external_data.data_source (
    id              UUID                     NOT NULL,
    provider        VARCHAR(100)             NOT NULL,
    record_type     VARCHAR(50)              NOT NULL,
    approval_status VARCHAR(30)              NOT NULL,
    approved_by     UUID,
    approved_at     TIMESTAMP WITH TIME ZONE,
    version         BIGINT                   NOT NULL,
    CONSTRAINT pk_data_source PRIMARY KEY (id),
    CONSTRAINT uk_data_source UNIQUE (provider, record_type),
    CONSTRAINT ck_data_source_approval_status CHECK (approval_status IN ('CANDIDATE', 'APPROVED', 'SUSPENDED'))
);

COMMENT ON TABLE external_data.data_source IS '情報源';
COMMENT ON COLUMN external_data.data_source.id IS '情報源 ID';
COMMENT ON COLUMN external_data.data_source.provider IS '提供元';
COMMENT ON COLUMN external_data.data_source.record_type IS '原本種別';
COMMENT ON COLUMN external_data.data_source.approval_status IS '選定・承認状態（CANDIDATE・APPROVED・SUSPENDED）';
COMMENT ON COLUMN external_data.data_source.approved_by IS '承認者';
COMMENT ON COLUMN external_data.data_source.approved_at IS '承認した時刻';
COMMENT ON COLUMN external_data.data_source.version IS '版（楽観ロック）';

CREATE TABLE external_data.ingestion (
    id              UUID                     NOT NULL,
    data_source_id  UUID                     NOT NULL,
    file_object_key VARCHAR(500)             NOT NULL,
    file_hash       VARCHAR(64)              NOT NULL,
    ingested_by     UUID                     NOT NULL,
    ingested_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    received_count  INTEGER                  NOT NULL,
    CONSTRAINT pk_ingestion PRIMARY KEY (id),
    CONSTRAINT fk_ingestion_data_source FOREIGN KEY (data_source_id) REFERENCES external_data.data_source (id)
);

COMMENT ON TABLE external_data.ingestion IS '取込';
COMMENT ON COLUMN external_data.ingestion.id IS '取込 ID';
COMMENT ON COLUMN external_data.ingestion.data_source_id IS '情報源 ID';
COMMENT ON COLUMN external_data.ingestion.file_object_key IS 'ファイルの原本のオブジェクトキー';
COMMENT ON COLUMN external_data.ingestion.file_hash IS 'ファイルのハッシュ';
COMMENT ON COLUMN external_data.ingestion.ingested_by IS '取込者';
COMMENT ON COLUMN external_data.ingestion.ingested_at IS '取込時刻';
COMMENT ON COLUMN external_data.ingestion.received_count IS '受信件数';

CREATE TABLE external_data.external_record (
    id                 UUID                     NOT NULL,
    provider           VARCHAR(100)             NOT NULL,
    record_type        VARCHAR(50)              NOT NULL,
    external_id        VARCHAR(200)             NOT NULL,
    external_version   VARCHAR(100)             NOT NULL,
    payload_hash       VARCHAR(64)              NOT NULL,
    payload_object_key VARCHAR(500)             NOT NULL,
    first_received_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_external_record PRIMARY KEY (id),
    CONSTRAINT uk_external_record_idempotency_key UNIQUE (provider, record_type, external_id, external_version)
);

COMMENT ON TABLE external_data.external_record IS '外部原本';
COMMENT ON COLUMN external_data.external_record.id IS '外部原本 ID';
COMMENT ON COLUMN external_data.external_record.provider IS '提供元';
COMMENT ON COLUMN external_data.external_record.record_type IS '原本種別';
COMMENT ON COLUMN external_data.external_record.external_id IS '外部 ID';
COMMENT ON COLUMN external_data.external_record.external_version IS '外部版';
COMMENT ON COLUMN external_data.external_record.payload_hash IS '最初に受け取った payload のハッシュ';
COMMENT ON COLUMN external_data.external_record.payload_object_key IS '最初に受け取った payload のオブジェクトキー';
COMMENT ON COLUMN external_data.external_record.first_received_at IS '最初の受信時刻';

CREATE TABLE external_data.received_record (
    id                  UUID                     NOT NULL,
    external_record_id  UUID                     NOT NULL,
    ingestion_id        UUID,
    payload_hash        VARCHAR(64)              NOT NULL,
    payload_object_key  VARCHAR(500)             NOT NULL,
    acquired_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    classification      VARCHAR(30)              NOT NULL,
    classification_note VARCHAR(1000),
    classified_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_received_record PRIMARY KEY (id),
    CONSTRAINT fk_received_record_external_record FOREIGN KEY (external_record_id)
        REFERENCES external_data.external_record (id),
    CONSTRAINT fk_received_record_ingestion FOREIGN KEY (ingestion_id) REFERENCES external_data.ingestion (id),
    CONSTRAINT ck_received_record_classification
        CHECK (classification IN ('ADOPTED', 'DUPLICATE', 'QUARANTINED', 'DISCREPANCY'))
);

CREATE INDEX ix_received_record_ingestion ON external_data.received_record (ingestion_id);
CREATE INDEX ix_received_record_classification ON external_data.received_record (classification);

COMMENT ON TABLE external_data.received_record IS '受信記録 [append-only]';
COMMENT ON COLUMN external_data.received_record.id IS '受信記録 ID';
COMMENT ON COLUMN external_data.received_record.external_record_id IS '外部原本 ID';
COMMENT ON COLUMN external_data.received_record.ingestion_id IS '取込 ID（取込によらない受信は NULL）';
COMMENT ON COLUMN external_data.received_record.payload_hash IS 'payload のハッシュ';
COMMENT ON COLUMN external_data.received_record.payload_object_key IS 'payload のオブジェクトキー';
COMMENT ON COLUMN external_data.received_record.acquired_at IS '取得時刻';
COMMENT ON COLUMN external_data.received_record.classification IS '分類（ADOPTED・DUPLICATE・QUARANTINED・DISCREPANCY）';
COMMENT ON COLUMN external_data.received_record.classification_note IS '分類の注記';
COMMENT ON COLUMN external_data.received_record.classified_at IS '分類の時刻';

CREATE TABLE external_data.received_record_decision (
    id                 UUID                     NOT NULL,
    received_record_id UUID                     NOT NULL,
    decision           VARCHAR(30)              NOT NULL,
    decided_by         UUID                     NOT NULL,
    decided_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    reason             VARCHAR(1000),
    CONSTRAINT pk_received_record_decision PRIMARY KEY (id),
    CONSTRAINT uk_received_record_decision_received_record UNIQUE (received_record_id),
    CONSTRAINT fk_received_record_decision_received_record FOREIGN KEY (received_record_id)
        REFERENCES external_data.received_record (id),
    CONSTRAINT ck_received_record_decision_decision CHECK (decision IN ('ADOPTED', 'REJECTED'))
);

COMMENT ON TABLE external_data.received_record_decision IS '受信記録の判断 [append-only]';
COMMENT ON COLUMN external_data.received_record_decision.id IS '受信記録の判断 ID';
COMMENT ON COLUMN external_data.received_record_decision.received_record_id IS '受信記録 ID';
COMMENT ON COLUMN external_data.received_record_decision.decision IS '判断（ADOPTED・REJECTED）';
COMMENT ON COLUMN external_data.received_record_decision.decided_by IS '判断者';
COMMENT ON COLUMN external_data.received_record_decision.decided_at IS '判断の時刻';
COMMENT ON COLUMN external_data.received_record_decision.reason IS '理由';

CREATE TABLE external_data.adopted_value (
    id                 UUID                     NOT NULL,
    received_record_id UUID                     NOT NULL,
    target_type        VARCHAR(30)              NOT NULL,
    target_key         VARCHAR(100)             NOT NULL,
    value_summary      VARCHAR(4000)            NOT NULL,
    info_version       VARCHAR(100)             NOT NULL,
    published_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_adopted_value PRIMARY KEY (id),
    CONSTRAINT uk_adopted_value_received_record UNIQUE (received_record_id),
    CONSTRAINT fk_adopted_value_received_record FOREIGN KEY (received_record_id)
        REFERENCES external_data.received_record (id),
    CONSTRAINT ck_adopted_value_target_type CHECK (target_type IN ('VOYAGE', 'MILESTONE'))
);

COMMENT ON TABLE external_data.adopted_value IS '採用値 [append-only]';
COMMENT ON COLUMN external_data.adopted_value.id IS '採用値 ID';
COMMENT ON COLUMN external_data.adopted_value.received_record_id IS '受信記録 ID';
COMMENT ON COLUMN external_data.adopted_value.target_type IS '対象の種類（VOYAGE・MILESTONE）';
COMMENT ON COLUMN external_data.adopted_value.target_key IS '対象のキー';
COMMENT ON COLUMN external_data.adopted_value.value_summary IS '値';
COMMENT ON COLUMN external_data.adopted_value.info_version IS '情報版';
COMMENT ON COLUMN external_data.adopted_value.published_at IS '公開した時刻';
