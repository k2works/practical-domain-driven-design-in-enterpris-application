-- ログインの失敗の回数の列の注釈を、今の数え方に合わせる（Bolt 34c。注釈だけで、列と制約は変えない）。
-- Bolt 30 で付けた注釈は「password と TOTP を合わせる。成功で 0 に戻す」だったが、Bolt 32 で回復コードの誤りも数え、0 に戻すのは
-- 2 つの要素がそろったログインの成功だけになった（IA-INV-03、データモデル）。既存のマイグレーションは Flyway の checksum があるので直さない。
COMMENT ON COLUMN identity.login_failure.consecutive_failures IS '連続の失敗の回数（ログインの password・TOTP・回復コードの誤りを合わせて数え、ロック中の試行は数えない。2 つの要素がそろったログインの成功で 0 に戻し、ロック中は戻さない。IA-INV-03、Bolt 32）';
