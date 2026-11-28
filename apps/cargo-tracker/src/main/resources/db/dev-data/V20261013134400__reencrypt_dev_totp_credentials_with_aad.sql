-- 開発用の利用者の TOTP の資格の暗号文を、持ち主の利用者 ID を関連データ（AAD）にした形に作り直す（Bolt 31b、ADR-011 の決定 5）。
-- V20261013111500 は適用済みなので書き換えず、この版で置き換える。dev のときだけ Flyway の場所に足す（DevDataLocationTest で守る）。
-- 秘密は V20261013111500 と同じ開発用の固定の値で、本物の秘密ではない。一致は DevTotpCredentialsDevProfileTest で確かめる。
UPDATE identity.totp_credential SET secret_encrypted = 'v1:N1GE/3Y7F2NB2eVwhHZoei9mYVsnSeA6OXir1G/DMZmtDHdjnEHjRmKiheitHOUs'
    WHERE user_id = '00000000-0000-0000-0000-000000000101';
UPDATE identity.totp_credential SET secret_encrypted = 'v1:UdN/6j0zLle1QJasBbnKlGrQYWaoHDzEBlC4nK1k/JCWZlBWJj6S+olZejnlcuR3'
    WHERE user_id = '00000000-0000-0000-0000-000000000102';
UPDATE identity.totp_credential SET secret_encrypted = 'v1:Ia4EH04tO3Qimqn5UzRblNdn5MDnRFhaJU+XQzNkM5chpLL0amlZoIFM/XQa6gEk'
    WHERE user_id = '00000000-0000-0000-0000-000000000301';
UPDATE identity.totp_credential SET secret_encrypted = 'v1:bnQOmAok9JcKZwMo+zYT9VjT6XuAHYPc7iJKmwjoMYmHTiPbkR7YbgjIm8lZHGrJ'
    WHERE user_id = '00000000-0000-0000-0000-000000000302';
UPDATE identity.totp_credential SET secret_encrypted = 'v1:FZ1bLUtKKeT6so+dinG9QBKdbjlvXjzcNcgYFiPOQhmbvJ79T2PiN5Jl9Lr2c1aZ'
    WHERE user_id = '00000000-0000-0000-0000-000000000303';
