-- session の主体の名前はログインのメールアドレス（最大 320 文字。app_user.email）。Spring Session の DDL の 100 文字では、
-- 長いメールアドレスの利用者のログインで session の保存が失敗する（Bolt 14 レビュー）
ALTER TABLE platform.spring_session ALTER COLUMN principal_name SET DATA TYPE VARCHAR(320);
