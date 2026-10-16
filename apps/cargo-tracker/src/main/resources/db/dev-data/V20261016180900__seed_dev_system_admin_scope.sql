-- 開発用のシステム管理者の管理範囲（Bolt 34b の計画。IA-INV-14）。dev のときだけ Flyway の場所に足す（application-dev.properties）。
-- 共通・ベンダーの場所に置かない（ステージング・本番に開発用の割り当てを作らない。DevDataLocationTest で守る）。
-- A 社（開発）と荷主 A（開発）を割り当てる。荷主 B（開発）は範囲外のまま残し、範囲外の利用者が見つからないことを試せるようにする。
INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_at) VALUES
    ('00000000-0000-0000-0000-000000000304', 'COMPANY', '00000000-0000-0000-0000-000000000003', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000304', 'COMPANY', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP);
