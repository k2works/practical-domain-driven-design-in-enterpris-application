-- 経路設計案件に荷主企業を足す（1 段目。Bolt 38g、US-16 AC4、IA-INV-02、データモデル `routing`）。
-- 社内の経路設計の画面を担当企業で絞るため、見積りの公開 API「経路条件の照会」の結果から案件を開くときに写す。
-- 3 段で入れる: この移行で null 可で足す → dev だけの V20261029100900 で既存の行を埋める → V20261029101000 で NOT NULL にする。
-- 見積りの表への外部キーは張らない（スキーマの所有。ADR-001）。
-- 索引は社内の案件一覧（S-05）の絞り込みと並び（見積有効期限の近い順）に合わせる。routing_case に状態の列はない（状態は route_version.status）。
ALTER TABLE routing.routing_case ADD COLUMN shipper_company_id UUID;

CREATE INDEX ix_routing_case_shipper ON routing.routing_case (shipper_company_id, quotation_expires_at);

COMMENT ON COLUMN routing.routing_case.shipper_company_id IS '荷主企業（見積りの公開 API の写し。担当範囲の判定に使う）';
