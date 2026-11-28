-- 監査記録に対象と変更前後を足す（Bolt 34、US-16 AC1、データモデル「アクセス・監査」）。
-- 役割の変更の履歴は別の表を作らず、監査記録 ROLES_CHANGED の対象と変更前後で代える（2026-10-16、human:kakimomokuri）。
-- 今の記録（ログインなど）は対象を持たないので NULL を許す。追記だけの表なので、列の追加は既存の行を変えない。
-- H2 と PostgreSQL の共通の構文で書くため、ALTER TABLE は 1 列ずつ書く（ADR-007）。
ALTER TABLE identity.audit_record ADD COLUMN target_type VARCHAR(50);
ALTER TABLE identity.audit_record ADD COLUMN target_id VARCHAR(100);
ALTER TABLE identity.audit_record ADD COLUMN target_version VARCHAR(50);
ALTER TABLE identity.audit_record ADD COLUMN before_state VARCHAR(4000);
ALTER TABLE identity.audit_record ADD COLUMN after_state VARCHAR(4000);

COMMENT ON COLUMN identity.audit_record.target_type IS '対象の種類（役割の変更では USER）';
COMMENT ON COLUMN identity.audit_record.target_id IS '対象の ID（役割の変更では利用者 ID）';
COMMENT ON COLUMN identity.audit_record.target_version IS '対象の版（役割の変更では更新した後の app_user.version）';
COMMENT ON COLUMN identity.audit_record.before_state IS '変更前の要約（役割の変更では役割の名前をカンマでつないだ文字列）';
COMMENT ON COLUMN identity.audit_record.after_state IS '変更後の要約（役割の変更では役割の名前をカンマでつないだ文字列）';
