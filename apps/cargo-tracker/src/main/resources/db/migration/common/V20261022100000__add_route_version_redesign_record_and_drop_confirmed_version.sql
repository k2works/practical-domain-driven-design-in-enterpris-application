-- US-07 AC4 の準備（Bolt 37d）。
-- 経路版に再設計要の記録（再設計要にした時刻と不適合の理由の区分）を持たせる。記録を持てるのは再設計要と旧版だけで、再設計要は必ず持つ。
-- 案件のヘッダの confirmed_route_version_no は書くだけで読まれず、再設計要にすると NULL で上書きされるので消す（D-72）。
-- 確定は案件に 1 つ（R-INV-05）は、集約と、PostgreSQL の部分一意インデックス（migration/postgresql）で守る。
ALTER TABLE routing.routing_case DROP CONSTRAINT fk_routing_case_confirmed_version;
ALTER TABLE routing.routing_case DROP COLUMN confirmed_route_version_no;

ALTER TABLE routing.route_version ADD COLUMN redesign_required_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE routing.route_version ADD COLUMN redesign_reasons VARCHAR(200);
ALTER TABLE routing.route_version ADD CONSTRAINT ck_route_version_redesign CHECK (
    (redesign_required_at IS NULL AND redesign_reasons IS NULL AND status <> 'REDESIGN_REQUIRED')
    OR (redesign_required_at IS NOT NULL AND redesign_reasons IS NOT NULL
        AND status IN ('REDESIGN_REQUIRED', 'SUPERSEDED')));

COMMENT ON COLUMN routing.route_version.redesign_required_at IS '再設計要にした時刻（再設計要の記録。再設計要と旧版だけ）';
COMMENT ON COLUMN routing.route_version.redesign_reasons IS '再設計要の理由の区分（DEADLINE_EXCEEDED・CONNECTION_TOO_SHORT・NOT_CONNECTABLE・VOYAGE_UNAVAILABLE を空白で区切る）';
