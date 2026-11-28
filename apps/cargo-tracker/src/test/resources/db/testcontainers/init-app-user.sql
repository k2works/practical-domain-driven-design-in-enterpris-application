-- テスト用の PostgreSQL に、アプリケーション利用者を作る（運用要件 DA-02 の db:init に相当）。
-- 権限は Flyway の afterMigrate のコールバックが与える。パスワードはテスト専用の値。
CREATE ROLE cargo_tracker_app LOGIN PASSWORD 'cargo_tracker_app_test';
