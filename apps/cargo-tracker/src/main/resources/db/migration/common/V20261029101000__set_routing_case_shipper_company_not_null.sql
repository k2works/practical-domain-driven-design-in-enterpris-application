-- 経路設計案件の荷主企業を NOT NULL にする（3 段目。Bolt 38g）。
-- 案件を開く処理（DE-16）は荷主企業を必ず写し、集約も必ず持つので、表で守る（null の分岐は作らない）。
-- Flyway は場所をまたいで版の順に流すので、dev では列の追加 → 埋め（db/dev-data の V20261029100900）→ この移行の順になる。
-- ステージング・本番には行がない。H2 の PostgreSQL 互換モードと PostgreSQL の両方で通る形（V20261010120000 の前例）。
ALTER TABLE routing.routing_case ALTER COLUMN shipper_company_id SET NOT NULL;
