-- デモ環境のサンプルの経路設計案件（Bolt 18）に、Bolt 19 で足した DE-16 の写し（見積有効期限・依頼者）を入れる。
-- 本番の案件は DE-16 の購読で入る。サンプルは依頼元の見積り（開発用のシード）から写す。
UPDATE routing.routing_case c
   SET quotation_expires_at = (SELECT q.expires_at FROM quotation.quotation q WHERE q.id = c.quotation_id),
       created_by = (SELECT q.responded_by FROM quotation.quotation q WHERE q.id = c.quotation_id)
 WHERE c.case_number IN ('RC-2026-0901', 'RC-2026-0902');
