-- 再設定のトークンの用途（Bolt 38b。確認ポイント 15、SEC-08、IA-INV-12）。password の再設定（RESET。30 分）と、システム管理者が登録した
-- 利用者への初回の設定のリンク（USER_INVITATION。利用者の招待。24 時間）を同じ表に置き、同じ A-06 で使う。既定の値は RESET（この
-- マイグレーションの前の行と、用途を省いた INSERT）。繰り返しの依頼の抑えは RESET の発行だけで数え、前の未使用のトークンの無効化は用途を
-- 問わない。CHECK は ADR-007 の前例どおり別の文で足す。
ALTER TABLE identity.password_reset_token ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'RESET';

ALTER TABLE identity.password_reset_token
    ADD CONSTRAINT ck_password_reset_token_purpose CHECK (purpose IN ('RESET', 'USER_INVITATION'));

COMMENT ON COLUMN identity.password_reset_token.purpose IS '用途（RESET 再設定、USER_INVITATION 利用者の招待）';
COMMENT ON COLUMN identity.password_reset_token.request_id IS '再設定の依頼または利用者の登録の ID（通知の契機の ID と同じ）';
COMMENT ON COLUMN identity.password_reset_token.expires_at IS '期限（発行から用途の期限。再設定 30 分、利用者の招待 24 時間）';
COMMENT ON COLUMN identity.password_reset_token.invalidated_at IS '無効にした時刻（同じ利用者の新しい発行、設定の完了、利用停止の確定で）';
