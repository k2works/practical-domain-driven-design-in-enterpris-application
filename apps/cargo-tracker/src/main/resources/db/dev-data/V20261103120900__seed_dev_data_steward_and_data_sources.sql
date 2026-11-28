-- 開発用のデータ責任者と情報源（Bolt 39b の確認ポイント 9）。dev のときだけ Flyway の場所に足す（application-dev.properties）。
-- 共通・ベンダーの場所に置かない（ステージング・本番に開発用の利用者と情報源を作らない。DevDataLocationTest で守る）。
-- データ責任者は A 社（開発）に所属し、担当範囲を持たない（W7 の確認ポイント 3）。password は営業担当者と同じ開発用の固定の値
-- （dev-password-staff）で、本物の秘密ではない。TOTP の資格と dev のログインの口座は、ログインが要る Bolt 39c で足す。
-- 情報源は架空の提供元で、原本種別は取込ファイルの種別の名前（VOYAGE・MILESTONE）。承認済みの 2 つと、拒否を確かめる候補 1 つ。
-- 承認した時刻はこの版の作成時刻の固定の値。
INSERT INTO identity.app_user
    (id, company_id, email, display_name, password_hash, status, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000307', '00000000-0000-0000-0000-000000000003',
     'data-steward@dev.cargo-tracker.example', '情報 管理（開発）',
     '{bcrypt}$2a$10$KdhLnXMR8Uw6WY7DwcSAAuHR0YJ1Xhc5waX1nfwUQqDq1unH/5GaK', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO identity.user_role (user_id, role, granted_at) VALUES
    ('00000000-0000-0000-0000-000000000307', 'DATA_STEWARD', CURRENT_TIMESTAMP);

INSERT INTO external_data.data_source
    (id, provider, record_type, approval_status, approved_by, approved_at, version) VALUES
    ('00000000-0000-0000-0000-000000003901', '開発海運（架空）', 'VOYAGE', 'APPROVED',
     '00000000-0000-0000-0000-000000000307', TIMESTAMP WITH TIME ZONE '2026-11-03 12:09:00+00', 0),
    ('00000000-0000-0000-0000-000000003902', '開発海運（架空）', 'MILESTONE', 'APPROVED',
     '00000000-0000-0000-0000-000000000307', TIMESTAMP WITH TIME ZONE '2026-11-03 12:09:00+00', 0),
    ('00000000-0000-0000-0000-000000003903', '開発港湾（架空）', 'VOYAGE', 'CANDIDATE', NULL, NULL, 0);
