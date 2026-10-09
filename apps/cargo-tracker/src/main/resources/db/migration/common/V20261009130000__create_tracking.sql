-- 追跡コンテキスト（tracking）。Bolt 25（ADR-015、T-INV-10〜12、データモデル `tracking`）。
-- 追跡の開始（DE-07 の購読で追跡記録を作り、確定した経路版の区間を予定として採用する）に使う表だけで作る。主要実績・訂正・有人案件などは
-- 使う Bolt で足す。予約・企業・経路設計の表への外部キーは張らない（スキーマの所有。ADR-001）。予約 ID・追跡番号・企業 ID は DE-07 の写し、
-- 経路版（案件番号・経路版番号）と区間は経路設計の公開 API の写し。行はイベントの購読でシステムが作るので、監査用の作成者・更新者の列を持たない。
CREATE SCHEMA IF NOT EXISTS tracking;

CREATE TABLE tracking.tracking_record (
    tracking_number           VARCHAR(20)              NOT NULL,
    booking_id                UUID                     NOT NULL,
    shipper_company_id        UUID                     NOT NULL,
    consignee_company_id      UUID                     NOT NULL,
    booking_status            VARCHAR(30)              NOT NULL,
    routing_case_number       VARCHAR(20),
    route_version_no          INTEGER,
    current_status            VARCHAR(30)              NOT NULL,
    status_basis_milestone_no INTEGER,
    under_review_reason       VARCHAR(1000),
    procedure_stage           VARCHAR(30),
    original_eta              TIMESTAMP WITH TIME ZONE,
    latest_eta                TIMESTAMP WITH TIME ZONE,
    last_acquired_at          TIMESTAMP WITH TIME ZONE,
    version                   BIGINT                   NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tracking_record PRIMARY KEY (tracking_number),
    -- T-INV-11: 予約 1 件に追跡記録 1 件。DE-07 の再配信・同時の配信の冪等の正（ADR-015）
    CONSTRAINT uk_tracking_record_booking UNIQUE (booking_id),
    CONSTRAINT ck_tracking_record_booking_status CHECK (booking_status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_tracking_record_current_status CHECK (current_status IN (
        'BOOKED', 'PICKUP_SCHEDULED', 'PICKED_UP', 'RECEIVED_AT_ORIGIN', 'IN_TRANSIT', 'TRANSSHIPPING',
        'ARRIVED_AT_DESTINATION', 'READY_FOR_DELIVERY', 'DELIVERED', 'UNDER_REVIEW')),
    -- 経路版の参照の 2 列はそろって NULL かそろって値を持つ
    CONSTRAINT ck_tracking_record_route_version CHECK (
        (routing_case_number IS NULL AND route_version_no IS NULL)
        OR (routing_case_number IS NOT NULL AND route_version_no >= 1))
);

COMMENT ON TABLE tracking.tracking_record IS '追跡記録';
COMMENT ON COLUMN tracking.tracking_record.tracking_number IS '追跡番号';
COMMENT ON COLUMN tracking.tracking_record.booking_id IS '予約 ID（写し）';
COMMENT ON COLUMN tracking.tracking_record.shipper_company_id IS '荷主企業 ID（写し）';
COMMENT ON COLUMN tracking.tracking_record.consignee_company_id IS '荷受人企業 ID（写し）';
COMMENT ON COLUMN tracking.tracking_record.booking_status IS '予約の状態（写し）';
COMMENT ON COLUMN tracking.tracking_record.routing_case_number IS '予定の経路版の案件番号';
COMMENT ON COLUMN tracking.tracking_record.route_version_no IS '予定の経路版番号';
COMMENT ON COLUMN tracking.tracking_record.current_status IS '現在状態（導出の結果の保存）';
COMMENT ON COLUMN tracking.tracking_record.status_basis_milestone_no IS '現在状態の根拠の実績番号';
COMMENT ON COLUMN tracking.tracking_record.under_review_reason IS '確認中の理由';
COMMENT ON COLUMN tracking.tracking_record.procedure_stage IS '手続き中の段階';
COMMENT ON COLUMN tracking.tracking_record.original_eta IS '当初の到着予定';
COMMENT ON COLUMN tracking.tracking_record.latest_eta IS '最新の到着見込み';
COMMENT ON COLUMN tracking.tracking_record.last_acquired_at IS '最終取得時刻';
COMMENT ON COLUMN tracking.tracking_record.version IS '版';
COMMENT ON COLUMN tracking.tracking_record.created_at IS '作成時刻';
COMMENT ON COLUMN tracking.tracking_record.updated_at IS '更新時刻';

CREATE TABLE tracking.scheduled_leg (
    tracking_number    VARCHAR(20)              NOT NULL,
    leg_no             INTEGER                  NOT NULL,
    voyage_number      VARCHAR(30)              NOT NULL,
    load_unlocode      CHAR(5)                  NOT NULL,
    discharge_unlocode CHAR(5)                  NOT NULL,
    departure_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_scheduled_leg PRIMARY KEY (tracking_number, leg_no),
    CONSTRAINT fk_scheduled_leg_tracking_record FOREIGN KEY (tracking_number)
        REFERENCES tracking.tracking_record (tracking_number),
    CONSTRAINT ck_scheduled_leg_no CHECK (leg_no >= 1),
    CONSTRAINT ck_scheduled_leg_arrival CHECK (arrival_at > departure_at)
);

COMMENT ON TABLE tracking.scheduled_leg IS '予定区間';
COMMENT ON COLUMN tracking.scheduled_leg.tracking_number IS '追跡番号';
COMMENT ON COLUMN tracking.scheduled_leg.leg_no IS '区間番号';
COMMENT ON COLUMN tracking.scheduled_leg.voyage_number IS '航海番号';
COMMENT ON COLUMN tracking.scheduled_leg.load_unlocode IS '積地';
COMMENT ON COLUMN tracking.scheduled_leg.discharge_unlocode IS '揚地';
COMMENT ON COLUMN tracking.scheduled_leg.departure_at IS '出発予定';
COMMENT ON COLUMN tracking.scheduled_leg.arrival_at IS '到着予定';
