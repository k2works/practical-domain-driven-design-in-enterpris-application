-- Bolt 39gb の開始準備の決定 (3)（確認ポイント 3）。追跡管理者が外部原本の下書きを採用した人と時刻を足す。
-- 外部原本の採用済み・確認中は両方に値を持ち（39gc が採用のときに確認中にしても人と時刻を残す）、ほかは両方 NULL。手入力の採用は登録者と登録時刻が兼ねる。
-- 外部原本の採用済みの行は今ない（採用の操作がなかった）ので埋め直さない。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE tracking.milestone ADD COLUMN adopted_by UUID;
ALTER TABLE tracking.milestone ADD COLUMN adopted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tracking.milestone ADD CONSTRAINT ck_milestone_adoption CHECK (
    (source_kind = 'EXTERNAL_RECORD' AND state IN ('ADOPTED', 'UNDER_REVIEW') AND adopted_by IS NOT NULL AND adopted_at IS NOT NULL)
    OR (NOT (source_kind = 'EXTERNAL_RECORD' AND state IN ('ADOPTED', 'UNDER_REVIEW')) AND adopted_by IS NULL AND adopted_at IS NULL)
);

COMMENT ON COLUMN tracking.milestone.adopted_by IS '採用者（外部原本の採用済み・確認中の実績だけが持つ。手入力の採用は登録者が兼ねる）';
COMMENT ON COLUMN tracking.milestone.adopted_at IS '採用時刻（外部原本の採用済み・確認中の実績だけが持つ。手入力の採用は登録時刻が兼ねる）';
