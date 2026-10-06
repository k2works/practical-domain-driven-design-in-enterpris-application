-- 荷主の回答（Bolt 12、Q-INV-18）。H2 では作れないため PostgreSQL だけに置く（2026-10-06 の決定）。

-- 1 つの輸送要求に、作成中・承認待ち・提示済み・詳細設計依頼済みの見積りは 1 つだけ（失効・置換済みは数えない）
DROP INDEX quotation.ux_quotation_active;
CREATE UNIQUE INDEX ux_quotation_active ON quotation.quotation (transport_request_id)
    WHERE status IN ('DRAFT', 'PENDING_APPROVAL', 'PRESENTED', 'ROUTING_REQUESTED');
