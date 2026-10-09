-- アプリケーション利用者への権限の付与と、追記専用の表からの UPDATE・DELETE の剥奪
-- （データモデル「追記専用」、運用要件 DA-02、ADR-007）。マイグレーションのたびに、表の作成と同じ配備で動く。
--
-- - アプリケーション利用者は db:init（運用準備）で作る。利用者の名前はプレースホルダー appuser で渡す
-- - 利用者がないとき: requireappuser が true（ステージング・本番）なら失敗させ、false（ローカル）なら何もしない
-- - 追記専用の表は、表の定義の印（COMMENT ON TABLE ... IS '<日本語名> [append-only]'）で選ぶ。名指しにしないので、
--   印を付ければ新しい表も保護される（名指しの書き忘れで保護が黙って外れることを防ぐ）
-- - 業務のスキーマを足したら、GRANT のスキーマの一覧も同じ変更で更新する（漏れるとアプリが権限不足で失敗する）
-- - 既定権限（ALTER DEFAULT PRIVILEGES）は使わない。Flyway 以外で作られた表に UPDATE・DELETE が付くのを防ぐ
-- - 連番（SEQUENCE、IDENTITY）を使う表を足したら、USAGE ON ALL SEQUENCES の付与も足す（いまは UUID だけ）
DO $$
DECLARE
    app_user text := '${appuser}';
    append_only record;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_user) THEN
        IF '${requireappuser}' = 'true' THEN
            RAISE EXCEPTION 'アプリケーション利用者 % がありません（運用要件 DA-02 の db:init を確かめる）', app_user;
        END IF;
        RAISE NOTICE 'アプリケーション利用者 % がないため、権限を与えない', app_user;
        RETURN;
    END IF;

    EXECUTE format('GRANT USAGE ON SCHEMA quotation, routing, identity, booking, tracking, platform TO %I', app_user);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA quotation, routing, identity, booking, tracking, platform TO %I',
                   app_user);

    FOR append_only IN
        SELECT n.nspname AS schema_name, c.relname AS table_name
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE c.relkind = 'r'
           AND obj_description(c.oid, 'pg_class') LIKE '% [append-only]'
    LOOP
        EXECUTE format('REVOKE UPDATE, DELETE ON %I.%I FROM %I',
                       append_only.schema_name, append_only.table_name, app_user);
    END LOOP;
END
$$;
