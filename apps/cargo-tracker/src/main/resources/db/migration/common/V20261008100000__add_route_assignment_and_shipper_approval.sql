-- 経路版の割当てと荷主の承認（Bolt 20、US-24 AC4・AC5、R-INV-11、Q-INV-10、データモデル `quotation`・`assigned_route_leg`）。
-- 状態に荷主承認待ち（AWAITING_SHIPPER_APPROVAL）と承認済み（APPROVED）を足す。経路版は経路設計の業務番号（案件番号）と
-- 経路版番号で参照し、経路設計の表に外部キーは張らない（スキーマの所有。ADR-001）。割り当てた経路の確定の時刻と区間は写して持ち、
-- 荷主の承認の画面（C-17）は経路設計に問い合わせない（ADR-014）。
-- 部分一意インデックス（Q-INV-18）に新しい状態を足すのは、H2 が部分インデックスを作れないため postgresql のマイグレーション
-- （V20261008100100）に置く。輸送要求の荷主承認待ち・予約待ちの値は、ck_transport_request_status に初めからある。
ALTER TABLE quotation.quotation ADD COLUMN routing_case_number VARCHAR(20);
ALTER TABLE quotation.quotation ADD COLUMN route_version_no INTEGER;
ALTER TABLE quotation.quotation ADD COLUMN route_confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE quotation.quotation ADD COLUMN shipper_approved_by UUID;
ALTER TABLE quotation.quotation ADD COLUMN shipper_approved_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE quotation.quotation DROP CONSTRAINT ck_quotation_status;
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_status
    CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PRESENTED', 'ROUTING_REQUESTED', 'AWAITING_SHIPPER_APPROVAL',
                      'APPROVED', 'EXPIRED', 'REPLACED'));

-- 経路版の参照の 3 列はそろって NULL かそろって値を持ち、荷主承認待ち・承認済みなら値を持つ
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_route_assigned
    CHECK (((routing_case_number IS NULL AND route_version_no IS NULL AND route_confirmed_at IS NULL)
            OR (routing_case_number IS NOT NULL AND route_version_no >= 1 AND route_confirmed_at IS NOT NULL))
        AND (status NOT IN ('AWAITING_SHIPPER_APPROVAL', 'APPROVED') OR routing_case_number IS NOT NULL));

-- 荷主承認の 2 列はそろって NULL かそろって値を持ち、承認済みなら値を持つ。承認は経路版の割当ての後
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_shipper_approved
    CHECK (((shipper_approved_by IS NULL AND shipper_approved_at IS NULL)
            OR (shipper_approved_by IS NOT NULL AND shipper_approved_at IS NOT NULL AND routing_case_number IS NOT NULL))
        AND (status <> 'APPROVED' OR shipper_approved_by IS NOT NULL));

COMMENT ON COLUMN quotation.quotation.routing_case_number IS '割り当てた経路の案件番号';
COMMENT ON COLUMN quotation.quotation.route_version_no IS '割り当てた経路版番号';
COMMENT ON COLUMN quotation.quotation.route_confirmed_at IS '割り当てた経路の確定の時刻（写し）';
COMMENT ON COLUMN quotation.quotation.shipper_approved_by IS '荷主承認の承認者';
COMMENT ON COLUMN quotation.quotation.shipper_approved_at IS '荷主承認の承認時刻';

CREATE TABLE quotation.assigned_route_leg (
    quotation_id       UUID                     NOT NULL,
    leg_no             INTEGER                  NOT NULL,
    voyage_number      VARCHAR(30)              NOT NULL,
    load_unlocode      CHAR(5)                  NOT NULL,
    discharge_unlocode CHAR(5)                  NOT NULL,
    departure_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_assigned_route_leg PRIMARY KEY (quotation_id, leg_no),
    CONSTRAINT fk_assigned_route_leg_quotation FOREIGN KEY (quotation_id) REFERENCES quotation.quotation (id),
    CONSTRAINT ck_assigned_route_leg_no CHECK (leg_no >= 1),
    CONSTRAINT ck_assigned_route_leg_arrival CHECK (arrival_at > departure_at)
);

COMMENT ON TABLE quotation.assigned_route_leg IS '割り当てた区間';
COMMENT ON COLUMN quotation.assigned_route_leg.quotation_id IS '見積り ID';
COMMENT ON COLUMN quotation.assigned_route_leg.leg_no IS '区間番号';
COMMENT ON COLUMN quotation.assigned_route_leg.voyage_number IS '航海番号';
COMMENT ON COLUMN quotation.assigned_route_leg.load_unlocode IS '積地';
COMMENT ON COLUMN quotation.assigned_route_leg.discharge_unlocode IS '揚地';
COMMENT ON COLUMN quotation.assigned_route_leg.departure_at IS '出発予定';
COMMENT ON COLUMN quotation.assigned_route_leg.arrival_at IS '到着予定';
