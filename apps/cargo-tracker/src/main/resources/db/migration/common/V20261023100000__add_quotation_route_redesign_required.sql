-- US-07 AC5 の見積りの側（Bolt 37e）。
-- 割り当てた経路の写しに、経路設計がその経路版を再設計要にした時刻（再設計要の印）を持たせる。印は経路版の事実の写しで、見積りの状態は
-- 変えない（status の CHECK は変えない）。印のある見積りは荷主の承認（Q-INV-10）と予約確定（B-INV-05）に使えない。再見積りは経路を写すので
-- 印も写る。印を付けるときは案件番号と経路版番号で見積りを引く（見積りは経路設計の案件 ID を持たない。D-4）ので、索引を付ける。
ALTER TABLE quotation.quotation ADD COLUMN route_redesign_required_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_route_redesign CHECK (
    route_redesign_required_at IS NULL OR routing_case_number IS NOT NULL);
CREATE INDEX ix_quotation_assigned_route ON quotation.quotation (routing_case_number, route_version_no);

COMMENT ON COLUMN quotation.quotation.route_redesign_required_at IS '割り当てた経路版を経路設計が再設計要にした時刻（再設計要の印。経路の割当てがあるときだけ）';
