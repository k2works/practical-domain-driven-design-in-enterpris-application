-- 開発用のデータ責任者（0307。V20261103120900 で足した）の TOTP の資格（Bolt 39c の開始準備の決定 (9)。S-16 にログインするため）。
-- dev のときだけ Flyway の場所に足す（application-dev.properties）。共通・ベンダーの場所に置かない（ステージング・本番に開発用の
-- 利用者の資格を作らない。DevDataLocationTest で守る）。TOTP の秘密は ASCII の "dev-totp-secret-" と利用者 ID の末尾 4 桁で、
-- dev の鍵で利用者 ID を AAD にして暗号化した値。平文の秘密は application-dev.properties の
-- cargotracker.dev-login.accounts[8].totp-secret にあり、一致を DevTotpCredentialsDevProfileTest で確かめる。
INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at) VALUES
    ('00000000-0000-0000-0000-000000000307', 'v1:ffn20qTy7rN9/jzTbdjzWcdysy247IY2soGkMiALWBMK3OtksKtFv+Eg+2N9T/Vs', 0, CURRENT_TIMESTAMP);
