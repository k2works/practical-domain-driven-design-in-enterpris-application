-- 外部原本の採否の監査記録の二重の防ぎ（W10 の Bolt 53-1、H-4。Bolt 39b3b のステップ 7 の決定）。
-- ハンドラーは「操作と対象で引いて、記録があれば書かない」が、照会と追記の間に別のインスタンスが同じ記録を書くと二重になる。
-- 複数のインスタンスにする前に、同じ操作・対象の記録を一意制約（部分索引）で 1 つに限る。外部原本の採否の 4 つの操作だけを対象にし、
-- ログインの失敗など同じ対象を持たない記録は何度でも書ける。
-- 部分索引（WHERE）は PostgreSQL のベンダーの移行に置く（H2 の dev は単一インスタンスで、ハンドラーの照会で足りる。ADR-007）。
-- 既存の行に重複があると作れずに失敗する（パイロット前の作成で、配備前に重複がないことを確かめる）。
CREATE UNIQUE INDEX ux_audit_record_external_once
    ON identity.audit_record (action, target_type, target_id)
 WHERE action IN ('EXTERNAL_RECORD_ADOPTED', 'EXTERNAL_RECORD_QUARANTINED', 'EXTERNAL_RECORD_DISCREPANCY_FOUND', 'EXTERNAL_RECORD_DECIDED');

COMMENT ON INDEX identity.ux_audit_record_external_once IS '外部原本の採否の監査記録は、同じ操作・受信記録に 1 つだけ（二重の配信の防ぎ）';
