-- 顧客 Web の照会が有人対応へ移ったかの更新（IR-INV-01 の 24 時間の規則。KPI-02。W9 の Bolt 44）。有人案件の起票のたびに、同じ追跡番号の
-- 顧客 Web の自己照会を受付時刻の範囲で探すので、追跡番号と受付時刻の索引を足す。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
CREATE INDEX ix_inquiry_record_tracking_received ON tracking.inquiry_record (tracking_number, received_at);
