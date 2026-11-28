-- Bolt 40-6（SC-INV-01〜03）。有人案件の 4 表と受付番号の採番の表。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
-- data_model との差: service_case_source に acquired_at（NOT NULL）を足した（共有カーネルの Source が取得時刻を必須にするため。2026-11-13 に人が承認）。

-- 受付番号の採番（年ごとの連番。Bolt 17 の案件番号の採番と同じ方式）
CREATE TABLE tracking.service_case_number_counter (
    number_year INTEGER NOT NULL,
    last_no     INTEGER NOT NULL,
    CONSTRAINT pk_service_case_number_counter PRIMARY KEY (number_year),
    CONSTRAINT ck_service_case_number_counter_last_no CHECK (last_no >= 0)
);

COMMENT ON TABLE tracking.service_case_number_counter IS '有人案件の受付番号の採番（年ごとの連番）';
COMMENT ON COLUMN tracking.service_case_number_counter.number_year IS '年';
COMMENT ON COLUMN tracking.service_case_number_counter.last_no IS 'その年に振った最後の連番';

-- 他のコンテキストの参照（荷主企業・予約・追跡番号・復旧照合）は外部キーを持たない。登録拒否の案件は知らない追跡番号を持ち得る。
CREATE TABLE tracking.service_case (
    case_number             VARCHAR(20)              NOT NULL,
    kind                    VARCHAR(30)              NOT NULL,
    shipper_company_id      UUID,
    booking_id              UUID,
    tracking_number         VARCHAR(20),
    cause                   VARCHAR(4000)            NOT NULL,
    priority                VARCHAR(30)              NOT NULL,
    department              VARCHAR(50)              NOT NULL,
    assignee_id             UUID,
    received_at             TIMESTAMP WITH TIME ZONE,
    receive_due_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    answer_due_at           TIMESTAMP WITH TIME ZONE,
    next_update_at          TIMESTAMP WITH TIME ZONE,
    old_version_ref         VARCHAR(200),
    new_version_ref         VARCHAR(200),
    manual_source_ref       VARCHAR(200),
    reconciliation_required BOOLEAN                  NOT NULL,
    reconciliation_id       UUID,
    acknowledged_at         TIMESTAMP WITH TIME ZONE,
    status                  VARCHAR(30)              NOT NULL,
    version                 BIGINT                   NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by              UUID                     NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by              UUID                     NOT NULL,
    CONSTRAINT pk_service_case PRIMARY KEY (case_number),
    CONSTRAINT ck_service_case_kind CHECK (kind IN (
        'CUSTOMER_INQUIRY', 'QUOTATION_CONSULTATION', 'AMENDMENT', 'REDESIGN', 'EXTERNAL_OUTAGE',
        'REJECTED_MILESTONE', 'SAGA_FAILURE')),
    CONSTRAINT ck_service_case_status CHECK (status IN (
        'RECEIVED', 'ESCALATED', 'IN_PROGRESS', 'ANSWERED', 'CLOSED', 'DUPLICATE_CLOSED')),
    CONSTRAINT ck_service_case_priority CHECK (priority IN ('HIGH', 'NORMAL')),
    CONSTRAINT ck_service_case_assignee CHECK (
        status NOT IN ('IN_PROGRESS', 'ANSWERED', 'CLOSED')
        OR (assignee_id IS NOT NULL AND received_at IS NOT NULL)),
    CONSTRAINT ck_service_case_reconciliation CHECK (
        (reconciliation_id IS NULL OR reconciliation_required = TRUE)
        AND NOT (status = 'CLOSED' AND reconciliation_required = TRUE AND reconciliation_id IS NULL))
);

CREATE INDEX ix_service_case_status_receive_due ON tracking.service_case (status, receive_due_at);
CREATE INDEX ix_service_case_shipper ON tracking.service_case (shipper_company_id);

COMMENT ON TABLE tracking.service_case IS '有人案件（顧客照会・業務例外）';
COMMENT ON COLUMN tracking.service_case.case_number IS '受付番号（SC-YYYY-NNNN）';
COMMENT ON COLUMN tracking.service_case.kind IS '種類';
COMMENT ON COLUMN tracking.service_case.shipper_company_id IS '荷主企業 ID（照会の範囲を限る）';
COMMENT ON COLUMN tracking.service_case.booking_id IS '予約 ID（参照）';
COMMENT ON COLUMN tracking.service_case.tracking_number IS '追跡番号（参照。知らない追跡番号も持ち得る）';
COMMENT ON COLUMN tracking.service_case.cause IS '原因・照会内容';
COMMENT ON COLUMN tracking.service_case.priority IS '優先度（HIGH、NORMAL）';
COMMENT ON COLUMN tracking.service_case.department IS '担当部門';
COMMENT ON COLUMN tracking.service_case.assignee_id IS '担当者（受領した利用者）';
COMMENT ON COLUMN tracking.service_case.received_at IS '受領時刻';
COMMENT ON COLUMN tracking.service_case.receive_due_at IS '受領期限（営業日カレンダーで数える）';
COMMENT ON COLUMN tracking.service_case.answer_due_at IS '回答期限';
COMMENT ON COLUMN tracking.service_case.next_update_at IS '次回更新日時';
COMMENT ON COLUMN tracking.service_case.old_version_ref IS '旧版の参照';
COMMENT ON COLUMN tracking.service_case.new_version_ref IS '新版の参照';
COMMENT ON COLUMN tracking.service_case.manual_source_ref IS '手動入力の出典の参照';
COMMENT ON COLUMN tracking.service_case.reconciliation_required IS '復旧照合が必要か';
COMMENT ON COLUMN tracking.service_case.reconciliation_id IS '復旧照合の完了の ID（外部データ。完了するまで空）';
COMMENT ON COLUMN tracking.service_case.acknowledged_at IS '営業時間外の起票に受付番号と次回更新日時を直ちに返した時刻';
COMMENT ON COLUMN tracking.service_case.status IS '状態';
COMMENT ON COLUMN tracking.service_case.version IS '楽観ロックの版';
COMMENT ON COLUMN tracking.service_case.created_at IS '起票時刻';
COMMENT ON COLUMN tracking.service_case.created_by IS '起票者';
COMMENT ON COLUMN tracking.service_case.updated_at IS '最終更新時刻';
COMMENT ON COLUMN tracking.service_case.updated_by IS '最終更新者';

CREATE TABLE tracking.service_case_source (
    case_number VARCHAR(20)              NOT NULL,
    source_no   INTEGER                  NOT NULL,
    source_kind VARCHAR(30)              NOT NULL,
    source_ref  VARCHAR(200)             NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_service_case_source PRIMARY KEY (case_number, source_no),
    CONSTRAINT fk_service_case_source_case FOREIGN KEY (case_number) REFERENCES tracking.service_case (case_number),
    CONSTRAINT ck_service_case_source_no CHECK (source_no >= 1)
);

COMMENT ON TABLE tracking.service_case_source IS '有人案件が参照した出典 [append-only]';
COMMENT ON COLUMN tracking.service_case_source.case_number IS '受付番号';
COMMENT ON COLUMN tracking.service_case_source.source_no IS '出典の番号（案件の中で 1 から）';
COMMENT ON COLUMN tracking.service_case_source.source_kind IS '出典の種類';
COMMENT ON COLUMN tracking.service_case_source.source_ref IS '出典の参照';
COMMENT ON COLUMN tracking.service_case_source.acquired_at IS '出典の取得時刻';

CREATE TABLE tracking.service_case_progress (
    case_number    VARCHAR(20)              NOT NULL,
    progress_no    INTEGER                  NOT NULL,
    content        VARCHAR(4000)            NOT NULL,
    next_update_at TIMESTAMP WITH TIME ZONE,
    updated_by     UUID                     NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_service_case_progress PRIMARY KEY (case_number, progress_no),
    CONSTRAINT fk_service_case_progress_case FOREIGN KEY (case_number) REFERENCES tracking.service_case (case_number),
    CONSTRAINT ck_service_case_progress_no CHECK (progress_no >= 1)
);

COMMENT ON TABLE tracking.service_case_progress IS '有人案件の進捗 [append-only]';
COMMENT ON COLUMN tracking.service_case_progress.case_number IS '受付番号';
COMMENT ON COLUMN tracking.service_case_progress.progress_no IS '進捗番号（案件の中で 1 から）';
COMMENT ON COLUMN tracking.service_case_progress.content IS '進捗の内容';
COMMENT ON COLUMN tracking.service_case_progress.next_update_at IS '次回更新日時（指定した場合）';
COMMENT ON COLUMN tracking.service_case_progress.updated_by IS '更新者';
COMMENT ON COLUMN tracking.service_case_progress.updated_at IS '更新時刻';

CREATE TABLE tracking.service_case_answer (
    case_number           VARCHAR(20)              NOT NULL,
    answer_no             INTEGER                  NOT NULL,
    content               VARCHAR(4000)            NOT NULL,
    answered_by           UUID                     NOT NULL,
    answered_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    customer_response     VARCHAR(4000),
    customer_responded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_service_case_answer PRIMARY KEY (case_number, answer_no),
    CONSTRAINT fk_service_case_answer_case FOREIGN KEY (case_number) REFERENCES tracking.service_case (case_number),
    CONSTRAINT ck_service_case_answer_no CHECK (answer_no >= 1),
    CONSTRAINT ck_service_case_answer_customer CHECK (
        (customer_response IS NULL AND customer_responded_at IS NULL)
        OR (customer_response IS NOT NULL AND customer_responded_at IS NOT NULL))
);

COMMENT ON TABLE tracking.service_case_answer IS '有人案件の回答（再開しても過去の分を保持する） [no-delete]';
COMMENT ON COLUMN tracking.service_case_answer.case_number IS '受付番号';
COMMENT ON COLUMN tracking.service_case_answer.answer_no IS '回答番号（案件の中で 1 から）';
COMMENT ON COLUMN tracking.service_case_answer.content IS '回答の内容';
COMMENT ON COLUMN tracking.service_case_answer.answered_by IS '回答者';
COMMENT ON COLUMN tracking.service_case_answer.answered_at IS '回答時刻';
COMMENT ON COLUMN tracking.service_case_answer.customer_response IS '顧客の回答（未解決など）';
COMMENT ON COLUMN tracking.service_case_answer.customer_responded_at IS '顧客が回答した時刻';
