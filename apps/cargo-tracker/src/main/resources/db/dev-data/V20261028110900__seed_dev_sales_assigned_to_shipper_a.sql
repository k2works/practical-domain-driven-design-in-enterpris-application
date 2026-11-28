-- 開発用の、荷主 A（開発）だけを担当する営業担当者（Bolt 38f の開始準備の決定 (12)。デモで担当範囲の絞り込みを見せる）。dev のときだけ
-- Flyway の場所に足す（application-dev.properties）。共通・ベンダーの場所に置かない（ステージング・本番に開発用の利用者を作らない。
-- DevDataLocationTest で守る）。既存の営業担当者（0301）は荷主 A・B を担当したまま。2 人を見比べると、荷主 B（開発）の見積依頼は
-- この営業担当者の受付一覧に出ず、URL を開くと「画面が見つかりません」になる。
-- password は営業担当者と同じ開発用の固定の値（dev-password-staff）で、本物の秘密ではない。TOTP の秘密は ASCII の "dev-totp-secret-" と
-- 利用者 ID の末尾 4 桁で、dev の鍵で利用者 ID を AAD にして暗号化した値。平文の秘密は application-dev.properties の
-- cargotracker.dev-login.accounts[n].totp-secret にあり、一致を DevTotpCredentialsDevProfileTest で確かめる。
INSERT INTO identity.app_user
    (id, company_id, email, display_name, password_hash, status, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000306', '00000000-0000-0000-0000-000000000003',
     'sales-shipper-a@dev.cargo-tracker.example', '営業 二郎（開発）',
     '{bcrypt}$2a$10$KdhLnXMR8Uw6WY7DwcSAAuHR0YJ1Xhc5waX1nfwUQqDq1unH/5GaK', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO identity.user_role (user_id, role, granted_at) VALUES
    ('00000000-0000-0000-0000-000000000306', 'SALES', CURRENT_TIMESTAMP);

INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at) VALUES
    ('00000000-0000-0000-0000-000000000306', 'v1:/iKHlvT329kwy2CmGwh529N+078VdNeCfSWWSdYk5iBcyRj/AhARzCyCAadlWmcb', 0, CURRENT_TIMESTAMP);

INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_at) VALUES
    ('00000000-0000-0000-0000-000000000306', 'COMPANY', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP);
