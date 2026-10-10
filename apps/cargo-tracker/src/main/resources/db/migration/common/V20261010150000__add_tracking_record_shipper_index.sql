-- 荷主の追跡の照会（C-10）の一覧の索引。Bolt 27（US-09 AC1、データモデルの索引の表）。
-- 荷主の一覧は荷主企業で絞って追跡の開始時刻（created_at）の新しい順に並べるので、並びの列を含めた複合の索引にする。
-- 荷受人の索引（consignee_company_id）は荷受人の照会（US-10、R1.1）で足す。
CREATE INDEX ix_tracking_record_shipper ON tracking.tracking_record (shipper_company_id, created_at);
