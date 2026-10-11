-- TOTP の資格・回復コード・ログインの失敗（Bolt 30、US-18、ADR-011、データモデル「アクセス・監査」）。
-- 関心事ごとに表を分け、どれも利用者の行と版（app_user.version）に触れずに 1 文の条件付きの更新で書く。
-- そのため 3 つの表は version を持たない（条件付きの更新の件数が楽観ロックの版の代わりになる）。
-- 秘密は暗号文だけを置き、回復コードはハッシュだけを置く（平文を表に書かない。SEC-09、SEC-16）。

-- 利用者ごとに 1 つ。最後に使った時間の刻みは単調に増える（IA-INV-10）
CREATE TABLE identity.totp_credential (
    user_id          UUID                     NOT NULL,
    secret_encrypted VARCHAR(500)             NOT NULL,
    last_used_step   BIGINT                   NOT NULL,
    enrolled_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_totp_credential PRIMARY KEY (user_id),
    CONSTRAINT fk_totp_credential_user FOREIGN KEY (user_id) REFERENCES identity.app_user (id),
    CONSTRAINT ck_totp_credential_last_used_step CHECK (last_used_step >= 0)
);

-- 利用者ごとに 10 個。発行のたびに消して入れ直すので、追記専用の印を付けない（IA-INV-11）。
-- bcrypt は塩が毎回違うので、(user_id, code_hash) の一意制約は重複を防げず、置かない
CREATE TABLE identity.recovery_code (
    id        UUID                     NOT NULL,
    user_id   UUID                     NOT NULL,
    code_hash VARCHAR(200)             NOT NULL,
    used_at   TIMESTAMP WITH TIME ZONE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_recovery_code PRIMARY KEY (id),
    CONSTRAINT fk_recovery_code_user FOREIGN KEY (user_id) REFERENCES identity.app_user (id)
);

CREATE INDEX ix_recovery_code_user ON identity.recovery_code (user_id);

-- 利用者ごとに 1 行。行のない利用者は「0 回・ロックなし」。存在しないメールアドレスの失敗では作らない（IA-INV-03）
CREATE TABLE identity.login_failure (
    user_id              UUID                     NOT NULL,
    consecutive_failures INTEGER                  NOT NULL,
    locked_until         TIMESTAMP WITH TIME ZONE,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_login_failure PRIMARY KEY (user_id),
    CONSTRAINT fk_login_failure_user FOREIGN KEY (user_id) REFERENCES identity.app_user (id),
    CONSTRAINT ck_login_failure_consecutive_failures CHECK (consecutive_failures >= 0)
);

COMMENT ON TABLE identity.totp_credential IS 'TOTP の資格';
COMMENT ON COLUMN identity.totp_credential.user_id IS '利用者 ID';
COMMENT ON COLUMN identity.totp_credential.secret_encrypted IS '暗号化した TOTP の秘密（平文は書かない。SEC-16）';
COMMENT ON COLUMN identity.totp_credential.last_used_step IS '最後に使った時間の刻み（epoch 秒 / 30。これ以下の刻みのコードは受け付けない）';
COMMENT ON COLUMN identity.totp_credential.enrolled_at IS '登録した時刻';

COMMENT ON TABLE identity.recovery_code IS '回復コード';
COMMENT ON COLUMN identity.recovery_code.id IS '回復コード ID';
COMMENT ON COLUMN identity.recovery_code.user_id IS '利用者 ID';
COMMENT ON COLUMN identity.recovery_code.code_hash IS '回復コードのハッシュ（bcrypt。平文は書かない。SEC-09）';
COMMENT ON COLUMN identity.recovery_code.used_at IS '使った時刻（未使用は null。一度だけ使える）';
COMMENT ON COLUMN identity.recovery_code.issued_at IS '発行した時刻';

COMMENT ON TABLE identity.login_failure IS 'ログインの失敗';
COMMENT ON COLUMN identity.login_failure.user_id IS '利用者 ID';
COMMENT ON COLUMN identity.login_failure.consecutive_failures IS '連続の失敗の回数（password と TOTP を合わせる。成功で 0 に戻す）';
COMMENT ON COLUMN identity.login_failure.locked_until IS 'ロックの期限（5 回目の失敗から 15 分。ロックしていなければ null）';
COMMENT ON COLUMN identity.login_failure.updated_at IS '更新した時刻';
