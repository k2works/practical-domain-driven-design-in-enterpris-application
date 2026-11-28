-- 開発用の利用者の TOTP の資格（Bolt 31、ADR-011 の dev の 4 層目）。dev のときだけ Flyway の場所に足す（application-dev.properties）。
-- 共通・ベンダーの場所に置かない（ステージング・本番に開発用の資格を作らない。DevDataLocationTest で守る）。
-- 秘密は利用者ごとに別の開発用の固定の値（ASCII の "dev-totp-secret-" と利用者 ID の末尾 4 桁）で、本物の秘密ではない。
-- 暗号文は dev の鍵（cargotracker.totp-secret-keys.v1）で暗号化した値。平文の秘密は application-dev.properties の
-- cargotracker.dev-login.accounts[n].totp-secret（Base32）にあり、両者の一致を DevTotpCredentialsDevProfileTest で確かめる。
INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at) VALUES
    ('00000000-0000-0000-0000-000000000101', 'v1:/d4y7O0BlTniSZ1rLkiGyQjyLL6+N3ne7FA0y2gXo19oCFYdYnG390HNyG6tTalO', 0, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000102', 'v1:DMmLUpL09MRP8s8S5YN4fxv8PLBuoAkgSSJqPPCfj02MfnWdgUltRnK9esCihfBf', 0, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000301', 'v1:GSH01D71IixCEpJQrvPCURB0GgLEoqKBUginGaYxi2Hxz1qzAC2hnhDjCKE8HZsO', 0, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000302', 'v1:UJ4FL8bfxg0ZeFP7Pd/dJhOdDl+aXRYjpC1XutOP483Sg/8Z66MG5si7/TlD4NWk', 0, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000303', 'v1:mIEvO2fbxQYtgkviTJ/v9xsqxxG7PZ/7+Hg/FVx9EdMLk075uXZOYA7KqgMaNSls', 0, CURRENT_TIMESTAMP);
