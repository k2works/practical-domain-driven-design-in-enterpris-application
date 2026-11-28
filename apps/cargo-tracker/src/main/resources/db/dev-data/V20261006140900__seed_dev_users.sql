-- 開発用の企業と利用者（Bolt 14、ADR-011 の決定 3、ADR-012）。dev のときだけ Flyway の場所に足す（application-dev.properties）。
-- 共通・ベンダーの場所に置かない（ステージング・本番に開発用の利用者を作らない。DevDataLocationTest で守る）。
-- ID は認証の前の仮の主体（Bolt 1・5）を引き継ぎ、開発環境の既存の流れと同じ利用者・企業にする。
-- password は本物の秘密ではない固定の開発用の値（荷主: dev-password-shipper、営業: dev-password-staff）で、bcrypt で保存する（SEC-07）。
INSERT INTO identity.company (id, name, kind, active, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', '荷主 A（開発）', 'SHIPPER', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', '荷主 B（開発）', 'SHIPPER', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'A 社（開発）', 'OPERATOR', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO identity.app_user
    (id, company_id, email, display_name, password_hash, status, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001',
     'shipper@dev.cargo-tracker.example', '荷主 太郎（開発）',
     '{bcrypt}$2a$10$8VBArBH3isskZmY/uml47Ov2JmySt9Fg0phh4efSlzNLiBnT2KDqO', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000002',
     'shipper-b@dev.cargo-tracker.example', '荷主 花子（開発）',
     '{bcrypt}$2a$10$8VBArBH3isskZmY/uml47Ov2JmySt9Fg0phh4efSlzNLiBnT2KDqO', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000003',
     'sales@dev.cargo-tracker.example', '営業 一郎（開発）',
     '{bcrypt}$2a$10$KdhLnXMR8Uw6WY7DwcSAAuHR0YJ1Xhc5waX1nfwUQqDq1unH/5GaK', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO identity.user_role (user_id, role, granted_at) VALUES
    ('00000000-0000-0000-0000-000000000101', 'SHIPPER', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000102', 'SHIPPER', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000301', 'SALES', CURRENT_TIMESTAMP);
