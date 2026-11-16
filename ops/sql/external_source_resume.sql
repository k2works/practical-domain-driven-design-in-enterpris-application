-- 停止中の情報源を承認済みに戻す（運用タスク external-source:resume。W10 の持ち越し H-2。台帳 D-102、W7 の議題）。
--
-- 情報源の選定・承認・停止は画面ではなく運用の手順で行う（マニュアル 12 章）。状態だけを変えて監査の記録が残らない変更にしないよう、
-- 状態の変更と監査記録（DATA_SOURCE_RESUMED）を 1 つのトランザクションで行う。
--
-- 変数（psql の -v で渡す）:
--   source_id  情報源 ID（external_data.data_source.id）
--   reason     再開する理由（空は不可、500 文字まで。例: 提供元の障害が復旧した）
--
-- 手順の約束（運用手順書）: 実行の前後に、その情報源の判断を待つ隔離の件数を S-17 で確かめる（この SQL も前後の件数を表示する）。
-- 停止中に取り込んだ隔離は、再開しても自動では変わらない（判断はデータ責任者が S-17 で行う）。
BEGIN;

DO $$
DECLARE
    reason_text text := TRIM(:'reason');
    current_status text;
BEGIN
    IF reason_text = '' OR char_length(reason_text) > 500 THEN
        RAISE EXCEPTION '理由は 1 文字以上 500 文字以下で指定してください';
    END IF;
    SELECT approval_status INTO current_status FROM external_data.data_source WHERE id = CAST(:'source_id' AS UUID);
    IF NOT FOUND THEN
        RAISE EXCEPTION '情報源がありません: %', :'source_id';
    END IF;
    IF current_status <> 'SUSPENDED' THEN
        RAISE EXCEPTION '情報源は停止中ではありません（現在: %）。再開できるのは停止中のものだけです', current_status;
    END IF;
END
$$;

-- 再開の前の、この情報源の判断を待つ隔離の件数（S-17 の「判断を待つ隔離」と同じ数え方: 隔離で、判断がまだないもの）
SELECT '再開の前の判断を待つ隔離: ' || COUNT(*)
  FROM external_data.received_record r
  JOIN external_data.ingestion i ON i.id = r.ingestion_id
 WHERE i.data_source_id = CAST(:'source_id' AS UUID)
   AND r.classification = 'QUARANTINED'
   AND NOT EXISTS (SELECT 1 FROM external_data.received_record_decision d WHERE d.received_record_id = r.id);

UPDATE external_data.data_source
   SET approval_status = 'APPROVED',
       approved_at = CURRENT_TIMESTAMP,
       version = version + 1
 WHERE id = CAST(:'source_id' AS UUID)
   AND approval_status = 'SUSPENDED';

-- 監査記録（操作者はなし。運用の手順で行った印を reason に、再開の理由を変更後の要約に残す）
INSERT INTO identity.audit_record
    (id, occurred_at, actor_user_id, actor_company_id, action, result, reason, correlation_id,
     target_type, target_id, target_version, before_state, after_state)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, NULL, NULL, 'DATA_SOURCE_RESUMED', 'SUCCESS', 'OPERATIONS_PROCEDURE', NULL,
       'DATA_SOURCE', CAST(id AS VARCHAR), CAST(version AS VARCHAR), 'SUSPENDED', 'APPROVED: ' || TRIM(:'reason')
  FROM external_data.data_source
 WHERE id = CAST(:'source_id' AS UUID);

COMMIT;
