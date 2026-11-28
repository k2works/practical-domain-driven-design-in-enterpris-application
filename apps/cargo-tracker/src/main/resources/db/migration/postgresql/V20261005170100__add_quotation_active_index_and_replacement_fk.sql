-- 見積りの置換（Bolt 11、Q-INV-18）。H2 では作れないため PostgreSQL だけに置く（2026-10-05 の決定）。

-- 1 つの輸送要求に、作成中・承認待ち・提示済みの見積りは 1 つだけ（失効・置換済みは数えない）
CREATE UNIQUE INDEX ux_quotation_active ON quotation.quotation (transport_request_id)
    WHERE status IN ('DRAFT', 'PENDING_APPROVAL', 'PRESENTED');

-- 置換先の見積り。再見積りは旧版を先に更新し（部分一意インデックスに触れないため）、新しい見積りを後に保存するので、
-- 存在の確認はコミットの時に行う
ALTER TABLE quotation.quotation ADD CONSTRAINT fk_quotation_replaced_by
    FOREIGN KEY (replaced_by_quotation_id) REFERENCES quotation.quotation (id)
    DEFERRABLE INITIALLY DEFERRED;
