-- 荷主の回答（Bolt 12、US-24 AC1、Q-INV-09、データモデル `quotation`）。
-- 状態に詳細設計依頼済み（ROUTING_REQUESTED）を足し、荷主の回答（shipper_response・responded_by・responded_at）を持たせる。
-- 回答の値は Bolt 12 では詳細経路設計へ進む（PROCEED）だけ。辞退（DECLINED）と辞退の理由は US-24 AC2 で足す。
-- 作成中・承認待ち・提示済みは 1 つだけの部分一意インデックスに詳細設計依頼済みを足すのは、H2 が部分インデックスを作れないため
-- postgresql のマイグレーション（V20261006100100）に置く（2026-10-06 の決定）。
ALTER TABLE quotation.quotation ADD COLUMN shipper_response VARCHAR(30);
ALTER TABLE quotation.quotation ADD COLUMN responded_by UUID;
ALTER TABLE quotation.quotation ADD COLUMN responded_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE quotation.quotation DROP CONSTRAINT ck_quotation_status;
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_status
    CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PRESENTED', 'ROUTING_REQUESTED', 'EXPIRED', 'REPLACED'));

ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_shipper_response
    CHECK (shipper_response IN ('PROCEED'));

-- 回答の 3 列はそろって NULL かそろって値を持ち、詳細設計依頼済みなら値を持つ
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_responded
    CHECK (((shipper_response IS NULL AND responded_by IS NULL AND responded_at IS NULL)
            OR (shipper_response IS NOT NULL AND responded_by IS NOT NULL AND responded_at IS NOT NULL))
        AND (status <> 'ROUTING_REQUESTED' OR shipper_response IS NOT NULL));

COMMENT ON COLUMN quotation.quotation.shipper_response IS '荷主の回答（PROCEED: 詳細経路設計へ進む）';
COMMENT ON COLUMN quotation.quotation.responded_by IS '回答者';
COMMENT ON COLUMN quotation.quotation.responded_at IS '回答時刻（詳細経路設計の依頼時刻）';
