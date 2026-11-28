-- 荷主の辞退（US-24 AC2。W9 の Bolt 46。D-86・ADR-018 の見直し）。辞退は失効（EXPIRED）として記録し、辞退者・辞退時刻は
-- 荷主の回答の列（responded_by・responded_at）に、回答の値は DECLINED で残す。辞退の理由だけを新しい列に足す（V20261006100000 で予告した形）。
ALTER TABLE quotation.quotation ADD COLUMN decline_reason VARCHAR(500);

ALTER TABLE quotation.quotation DROP CONSTRAINT ck_quotation_shipper_response;
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_shipper_response
    CHECK (shipper_response IN ('PROCEED', 'DECLINED'));

-- 辞退（DECLINED）は理由を持ち、状態は失効。辞退でなければ理由を持たない
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_declined
    CHECK ((shipper_response = 'DECLINED' AND decline_reason IS NOT NULL AND status = 'EXPIRED')
        OR (COALESCE(shipper_response, 'NONE') <> 'DECLINED' AND decline_reason IS NULL));

COMMENT ON COLUMN quotation.quotation.shipper_response IS '荷主の回答（PROCEED: 詳細経路設計へ進む、DECLINED: 辞退。辞退は状態を失効にして記録する）';
COMMENT ON COLUMN quotation.quotation.decline_reason IS '辞退の理由（荷主が辞退したときだけ。1〜500 文字）';
