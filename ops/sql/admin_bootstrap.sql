-- 最初のシステム管理者を作る（運用タスク admin:bootstrap。W10 の持ち越し H-1、bolt_54_plan.md の D-3）。
--
-- 監督の役割（システム管理者・監査担当者）は画面の登録では付けられない決まり（RoleAssignmentPolicy）なので、最初の 1 人は運用の手順で作る
-- （Bolt 38e の決定。管理者がいないと誰もログインできず、ほかの利用者も登録できない）。
--
-- 変数（psql の -v で渡す。値は引用して使う）:
--   admin_email   最初の管理者のメールアドレス（前後の空白を除き小文字で保存する）
--   admin_name    表示名
--   company_name  管理者の所属企業（A 社。種類 OPERATOR）の名前。同じ名前の企業があればそれを使い、なければ作る
--
-- 作るもの: 企業（なければ）、利用者（利用中。password は**使えないハッシュ**）、役割 SYSTEM_ADMIN、管理範囲（自社）、監査記録。
-- 本人の password は、ログイン画面の「password を忘れた場合」（password の再設定。メールのリンク）で設定する。平文も実際のハッシュも
-- 手順に出ない。TOTP は初回のログインで登録する。
--
-- すでにシステム管理者がいるときは何も作らずに失敗する（2 人目以降は画面の登録で作れない役割だが、この手順で何人も作らせない）。
-- 1 つのトランザクションで流す（途中で失敗したら何も残さない）。変数の値は信頼できる運用者が渡す（利用者の入力ではない）。
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM identity.user_role WHERE role = 'SYSTEM_ADMIN') THEN
        RAISE EXCEPTION 'すでにシステム管理者がいます。最初の管理者の作成は 1 回だけです';
    END IF;
END
$$;

INSERT INTO identity.company (id, name, kind, active, version, created_at, updated_at)
SELECT gen_random_uuid(), :'company_name', 'OPERATOR', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
 WHERE NOT EXISTS (SELECT 1 FROM identity.company WHERE name = :'company_name' AND kind = 'OPERATOR');

-- 使えないハッシュ: bcrypt の形でない値にし、どの password とも一致させない（平文を持たない。照合は常に失敗する）
INSERT INTO identity.app_user (id, company_id, email, display_name, password_hash, status, version, created_at, updated_at)
SELECT gen_random_uuid(),
       (SELECT id FROM identity.company WHERE name = :'company_name' AND kind = 'OPERATOR' ORDER BY created_at LIMIT 1),
       LOWER(TRIM(:'admin_email')),
       TRIM(:'admin_name'),
       '{bcrypt}!UNUSABLE',
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP;

INSERT INTO identity.user_role (user_id, role, granted_by, granted_at)
SELECT id, 'SYSTEM_ADMIN', NULL, CURRENT_TIMESTAMP FROM identity.app_user WHERE email = LOWER(TRIM(:'admin_email'));

INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_by, granted_at)
SELECT id, 'COMPANY', company_id, NULL, CURRENT_TIMESTAMP FROM identity.app_user WHERE email = LOWER(TRIM(:'admin_email'));

-- 監査記録（操作者はなし。運用の手順で作った印を reason に残す。メールアドレスと表示名は書かない）
INSERT INTO identity.audit_record
    (id, occurred_at, actor_user_id, actor_company_id, action, result, reason, correlation_id,
     target_type, target_id, target_version, before_state, after_state)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, NULL, NULL, 'USER_REGISTERED', 'SUCCESS', 'INITIAL_ADMINISTRATOR', NULL,
       'USER', CAST(id AS VARCHAR), '0', NULL, 'SYSTEM_ADMIN'
  FROM identity.app_user WHERE email = LOWER(TRIM(:'admin_email'));

COMMIT;
