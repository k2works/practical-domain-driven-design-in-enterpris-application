-- 開発用のシステム管理者と、役割の変更を試す相手の利用者（Bolt 34 の確認ポイント 11）。dev のときだけ Flyway の場所に足す
-- （application-dev.properties）。共通・ベンダーの場所に置かない（ステージング・本番に開発用の利用者を作らない。DevDataLocationTest で守る）。
-- どちらも A 社（開発）に所属する。password は営業担当者と同じ開発用の固定の値（dev-password-staff）で、本物の秘密ではない。
-- TOTP の秘密は ASCII の "dev-totp-secret-" と利用者 ID の末尾 4 桁で、dev の鍵で利用者 ID を AAD にして暗号化した値（Bolt 31b）。
-- 平文の秘密は application-dev.properties の cargotracker.dev-login.accounts[n].totp-secret にあり、一致を DevTotpCredentialsDevProfileTest で確かめる。
INSERT INTO identity.app_user
    (id, company_id, email, display_name, password_hash, status, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000003',
     'system-admin@dev.cargo-tracker.example', '管理 次郎（開発）',
     '{bcrypt}$2a$10$KdhLnXMR8Uw6WY7DwcSAAuHR0YJ1Xhc5waX1nfwUQqDq1unH/5GaK', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000305', '00000000-0000-0000-0000-000000000003',
     'support@dev.cargo-tracker.example', '窓口 三郎（開発）',
     '{bcrypt}$2a$10$KdhLnXMR8Uw6WY7DwcSAAuHR0YJ1Xhc5waX1nfwUQqDq1unH/5GaK', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO identity.user_role (user_id, role, granted_at) VALUES
    ('00000000-0000-0000-0000-000000000304', 'SYSTEM_ADMIN', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000305', 'CUSTOMER_SUPPORT', CURRENT_TIMESTAMP);

INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at) VALUES
    ('00000000-0000-0000-0000-000000000304', 'v1:LFzvDcdh+lDi8oUfG68NhI/tzie3WGz42iGDhjlJwFHREiTk/X66jxoRETV3YbsT', 0, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000305', 'v1:+WAtpNU03411Sx/tSI5dAgyWft2EkWxmnmiAqHf091CxD9agRVffUMBxQv57Mt+b', 0, CURRENT_TIMESTAMP);
