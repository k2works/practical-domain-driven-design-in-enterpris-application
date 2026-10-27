-- 監査記録を対象で引く索引。Bolt 38c2（US-16 AC5、#50、データモデルの索引の表）。
-- S-20 の詳細で停止中の利用者の今の停止の記録（対象の利用者の停止と再開のうち最新の 1 行）を引くため、対象と時刻の複合の索引にする。
-- 監査証跡の照会（US-17）も同じ索引を使う予定（データモデルの索引の表）。H2 と PostgreSQL の共通の構文で書き、CONCURRENTLY は使わない（H2 が持たず、
-- 本番の配備（W10）の前に足すので待ちの問題はない。ADR-007）。
CREATE INDEX ix_audit_record_target ON identity.audit_record (target_type, target_id, occurred_at);
