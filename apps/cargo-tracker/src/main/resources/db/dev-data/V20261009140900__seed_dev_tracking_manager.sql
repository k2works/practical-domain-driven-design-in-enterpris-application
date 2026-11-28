-- 開発用の追跡管理者（Bolt 26 の確認ポイント 3）。dev のときだけ Flyway の場所に足す（application-dev.properties）。
-- 共通・ベンダーの場所に置かない（ステージング・本番に開発用の利用者を作らない。DevDataLocationTest で守る）。
-- A 社（開発）に所属する。password は営業担当者と同じ開発用の固定の値（dev-password-staff）で、本物の秘密ではない。
INSERT INTO identity.app_user
    (id, company_id, email, display_name, password_hash, status, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000003',
     'tracking-manager@dev.cargo-tracker.example', '追跡 管理（開発）',
     '{bcrypt}$2a$10$KdhLnXMR8Uw6WY7DwcSAAuHR0YJ1Xhc5waX1nfwUQqDq1unH/5GaK', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO identity.user_role (user_id, role, granted_at) VALUES
    ('00000000-0000-0000-0000-000000000303', 'TRACKING_MANAGER', CURRENT_TIMESTAMP);
