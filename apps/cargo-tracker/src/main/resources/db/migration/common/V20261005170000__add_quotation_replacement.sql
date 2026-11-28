-- 見積りの失効と置換（Bolt 11、US-03 AC5、Q-INV-07・18、データモデル `quotation`）。
-- 状態に失効（EXPIRED）・置換済み（REPLACED）を足し、置換先の見積り（replaced_by_quotation_id）を持たせる。
-- 置換先の FK と、作成中・承認待ち・提示済みは 1 つだけの部分一意インデックスは、H2 が遅延の FK と部分インデックスを
-- 作れないため postgresql のマイグレーション（V20261005170100）に置く（2026-10-05 の決定）。
ALTER TABLE quotation.quotation ADD COLUMN replaced_by_quotation_id UUID;

ALTER TABLE quotation.quotation DROP CONSTRAINT ck_quotation_status;
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_status
    CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PRESENTED', 'EXPIRED', 'REPLACED'));

-- 置換先は置換済みのときだけ持つ
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_replaced
    CHECK ((status = 'REPLACED' AND replaced_by_quotation_id IS NOT NULL)
        OR (status <> 'REPLACED' AND replaced_by_quotation_id IS NULL));

COMMENT ON COLUMN quotation.quotation.replaced_by_quotation_id IS '置換先の見積り ID（置換済みのとき）';
-- 社内承認して提示するは 1 つの操作なので、社内承認時刻と提示時刻は同じ値（Bolt 9・10 レビュー R-14）
COMMENT ON COLUMN quotation.quotation.internal_approved_at IS '社内承認時刻（提示時刻と同じ）';
COMMENT ON COLUMN quotation.quotation.presented_at IS '提示時刻（KPI-01 の終了。社内承認時刻と同じ）';
