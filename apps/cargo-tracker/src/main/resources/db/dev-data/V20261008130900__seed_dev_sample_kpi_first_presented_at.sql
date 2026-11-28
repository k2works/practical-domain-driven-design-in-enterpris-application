-- デモ環境のサンプルの KPI 計測記録（Bolt 18）に、Bolt 21 で足した最初の提示時刻を入れる。
-- 本番の記録は DE-03 の購読で入る。サンプルは提示済みの見積り（開発用のシード）の提示時刻のうち、いちばん早いものを写す。
UPDATE identity.kpi_observation k
   SET first_presented_at = (SELECT MIN(q.presented_at)
                               FROM quotation.quotation q
                              WHERE q.transport_request_id = k.transport_request_id
                                AND q.presented_at IS NOT NULL)
 WHERE k.transport_request_number IN ('TR-2026-0903', 'TR-2026-0904', 'TR-2026-0905');
