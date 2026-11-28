-- password の再設定のトークン（Bolt 33、US-18 AC8、SEC-08、IA-INV-12、ADR-017、データモデル「アクセス・監査」）。
-- リンクに入れる値は表に書かず、SHA-256 のハッシュ（16 進 64 文字）だけを置く（平文を表に書かない）。
-- 依頼 1 件にトークン 1 つ（request_id の一意。モジュールのイベントの再配信で 2 通送らない）。使った時刻（Bolt 33a）と無効にした時刻は
-- 1 文の条件付きの更新（どちらも NULL のときだけ）で書くので、版の列を持たない（データモデルの例外）。行はシステムが作るので、
-- 監査用の作成者・更新者の列を持たない。
CREATE TABLE identity.password_reset_token (
    id             UUID                     NOT NULL,
    request_id     UUID                     NOT NULL,
    user_id        UUID                     NOT NULL,
    token_hash     CHAR(64)                 NOT NULL,
    issued_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at        TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_request UNIQUE (request_id),
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES identity.app_user (id),
    CONSTRAINT ck_password_reset_token_expires_at CHECK (expires_at > issued_at),
    -- 使ったトークンと無効にしたトークンは両立しない
    CONSTRAINT ck_password_reset_token_used_or_invalidated CHECK (used_at IS NULL OR invalidated_at IS NULL)
);

-- 利用者の前の未使用のトークンの無効化と、繰り返しの依頼の判定（前の発行の時刻）に使う
CREATE INDEX ix_password_reset_token_user_issued ON identity.password_reset_token (user_id, issued_at);

COMMENT ON TABLE identity.password_reset_token IS 'password の再設定のトークン';
COMMENT ON COLUMN identity.password_reset_token.id IS 'トークン ID';
COMMENT ON COLUMN identity.password_reset_token.request_id IS '再設定の依頼の ID（通知の契機の ID と同じ）';
COMMENT ON COLUMN identity.password_reset_token.user_id IS '利用者 ID';
COMMENT ON COLUMN identity.password_reset_token.token_hash IS 'トークンの SHA-256 のハッシュ（16 進。平文は書かない）';
COMMENT ON COLUMN identity.password_reset_token.issued_at IS '発行時刻';
COMMENT ON COLUMN identity.password_reset_token.expires_at IS '期限（発行から 30 分）';
COMMENT ON COLUMN identity.password_reset_token.used_at IS '使った時刻';
COMMENT ON COLUMN identity.password_reset_token.invalidated_at IS '無効にした時刻（同じ利用者の新しい発行で）';
