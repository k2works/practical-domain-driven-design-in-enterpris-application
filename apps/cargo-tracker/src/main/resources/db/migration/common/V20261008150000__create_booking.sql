-- 予約コンテキスト（booking）。Bolt 23（US-04 AC1・AC2、B-INV-01・08・11、データモデル `booking`）。
-- 本予約の確定と予約サガの開始に使う表だけで作る。変更・取消申請（US-05）と処理済みコマンド（B-INV-03、Bolt 24）は使う Bolt で足す。
-- 見積り・経路設計の表への外部キーは張らない（スキーマの所有。ADR-001）。輸送要求・見積りの ID、業務番号、経路版（案件番号・経路版番号）は
-- 見積りの公開 API の写し（ADR-016）。見積りは割り当てた経路を案件番号で持つため、予約版も案件 ID でなく案件番号を持つ。
CREATE SCHEMA IF NOT EXISTS booking;

CREATE TABLE booking.booking (
    id                       UUID                     NOT NULL,
    tracking_number          VARCHAR(20)              NOT NULL,
    transport_request_number VARCHAR(20)              NOT NULL,
    shipper_company_id       UUID                     NOT NULL,
    status                   VARCHAR(30)              NOT NULL,
    transport_phase          VARCHAR(30)              NOT NULL,
    current_version_no       INTEGER                  NOT NULL,
    version                  BIGINT                   NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by               UUID                     NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by               UUID                     NOT NULL,
    CONSTRAINT pk_booking PRIMARY KEY (id),
    CONSTRAINT uk_booking_tracking_number UNIQUE (tracking_number),
    CONSTRAINT ck_booking_status CHECK (status IN (
        'CONFIRMED', 'AMENDMENT_PENDING', 'CANCELLATION_PENDING', 'AMENDING', 'CANCELLED', 'IN_TRANSIT', 'COMPLETED')),
    CONSTRAINT ck_booking_transport_phase CHECK (transport_phase IN ('BEFORE_PICKUP', 'AFTER_PICKUP', 'COMPLETED')),
    CONSTRAINT ck_booking_current_version_no CHECK (current_version_no >= 1)
);

CREATE INDEX ix_booking_shipper_status ON booking.booking (shipper_company_id, status);
CREATE INDEX ix_booking_transport_request_number ON booking.booking (transport_request_number);

-- 予約版は不変（B-INV-08）。追記専用で、UPDATE・DELETE の権限は afterMigrate で外す
CREATE TABLE booking.booking_version (
    booking_id                   UUID                     NOT NULL,
    booking_version_no           INTEGER                  NOT NULL,
    transport_request_id         UUID                     NOT NULL,
    transport_request_version_no INTEGER                  NOT NULL,
    quotation_id                 UUID                     NOT NULL,
    routing_case_number          VARCHAR(20)              NOT NULL,
    route_version_no             INTEGER                  NOT NULL,
    consignee_company_id         UUID                     NOT NULL,
    cargo_category               VARCHAR(30)              NOT NULL,
    cargo_summary                VARCHAR(1000)            NOT NULL,
    shipper_approver_id          UUID                     NOT NULL,
    confirmed_by                 UUID                     NOT NULL,
    committed_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_booking_version PRIMARY KEY (booking_id, booking_version_no),
    CONSTRAINT fk_booking_version_booking FOREIGN KEY (booking_id) REFERENCES booking.booking (id),
    CONSTRAINT uk_booking_version_quotation UNIQUE (quotation_id),
    CONSTRAINT ck_booking_version_no CHECK (booking_version_no >= 1),
    CONSTRAINT ck_booking_version_request_version_no CHECK (transport_request_version_no >= 1),
    CONSTRAINT ck_booking_version_route_version_no CHECK (route_version_no >= 1)
);

CREATE TABLE booking.booking_saga (
    id                  UUID                     NOT NULL,
    booking_id          UUID                     NOT NULL,
    tracking_number     VARCHAR(20)              NOT NULL,
    status              VARCHAR(30)              NOT NULL,
    current_step        VARCHAR(50)              NOT NULL,
    attempts            INTEGER                  NOT NULL,
    next_retry_at       TIMESTAMP WITH TIME ZONE,
    last_error          VARCHAR(4000),
    service_case_number VARCHAR(20),
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT                   NOT NULL,
    CONSTRAINT pk_booking_saga PRIMARY KEY (id),
    CONSTRAINT fk_booking_saga_booking FOREIGN KEY (booking_id) REFERENCES booking.booking (id),
    CONSTRAINT uk_booking_saga_booking UNIQUE (booking_id),
    CONSTRAINT ck_booking_saga_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED', 'NEEDS_HUMAN')),
    CONSTRAINT ck_booking_saga_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_booking_saga_status_retry ON booking.booking_saga (status, next_retry_at);

COMMENT ON TABLE booking.booking IS '貨物予約';
COMMENT ON COLUMN booking.booking.id IS '予約 ID';
COMMENT ON COLUMN booking.booking.tracking_number IS '追跡番号';
COMMENT ON COLUMN booking.booking.transport_request_number IS '業務番号（見積りの写し）';
COMMENT ON COLUMN booking.booking.shipper_company_id IS '荷主企業 ID';
COMMENT ON COLUMN booking.booking.status IS '状態';
COMMENT ON COLUMN booking.booking.transport_phase IS '輸送段階';
COMMENT ON COLUMN booking.booking.current_version_no IS '現在の予約版番号';
COMMENT ON COLUMN booking.booking.version IS '版（楽観ロック）';
COMMENT ON COLUMN booking.booking.created_at IS '作成日時';
COMMENT ON COLUMN booking.booking.created_by IS '作成者';
COMMENT ON COLUMN booking.booking.updated_at IS '更新日時';
COMMENT ON COLUMN booking.booking.updated_by IS '更新者';

COMMENT ON TABLE booking.booking_version IS '予約版 [append-only]';
COMMENT ON COLUMN booking.booking_version.booking_id IS '予約 ID';
COMMENT ON COLUMN booking.booking_version.booking_version_no IS '予約版番号';
COMMENT ON COLUMN booking.booking_version.transport_request_id IS '輸送要求 ID（見積りの写し）';
COMMENT ON COLUMN booking.booking_version.transport_request_version_no IS '輸送要求版番号（見積りの写し）';
COMMENT ON COLUMN booking.booking_version.quotation_id IS '見積り ID（見積りの写し）';
COMMENT ON COLUMN booking.booking_version.routing_case_number IS '案件番号（見積りが割り当てた経路の写し）';
COMMENT ON COLUMN booking.booking_version.route_version_no IS '経路版番号（見積りが割り当てた経路の写し）';
COMMENT ON COLUMN booking.booking_version.consignee_company_id IS '荷受人企業 ID（見積りの写し）';
COMMENT ON COLUMN booking.booking_version.cargo_category IS '貨物区分（見積りの写し）';
COMMENT ON COLUMN booking.booking_version.cargo_summary IS '貨物の要約（見積りの写し）';
COMMENT ON COLUMN booking.booking_version.shipper_approver_id IS '荷主承認者';
COMMENT ON COLUMN booking.booking_version.confirmed_by IS '確定者';
COMMENT ON COLUMN booking.booking_version.committed_at IS 'commit 時刻';

COMMENT ON TABLE booking.booking_saga IS '予約サガ';
COMMENT ON COLUMN booking.booking_saga.id IS '予約サガ ID';
COMMENT ON COLUMN booking.booking_saga.booking_id IS '予約 ID';
COMMENT ON COLUMN booking.booking_saga.tracking_number IS '追跡番号';
COMMENT ON COLUMN booking.booking_saga.status IS '状態';
COMMENT ON COLUMN booking.booking_saga.current_step IS '現在の段階';
COMMENT ON COLUMN booking.booking_saga.attempts IS '試行回数';
COMMENT ON COLUMN booking.booking_saga.next_retry_at IS '次の再試行の日時';
COMMENT ON COLUMN booking.booking_saga.last_error IS '最後の失敗の理由';
COMMENT ON COLUMN booking.booking_saga.service_case_number IS '有人案件番号';
COMMENT ON COLUMN booking.booking_saga.started_at IS '開始日時';
COMMENT ON COLUMN booking.booking_saga.updated_at IS '更新日時';
COMMENT ON COLUMN booking.booking_saga.version IS '版（楽観ロック）';
