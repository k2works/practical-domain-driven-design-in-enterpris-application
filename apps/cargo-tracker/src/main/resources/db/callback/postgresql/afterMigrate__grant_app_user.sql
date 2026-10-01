-- アプリケーション利用者への権限の付与と、追記専用の表からの UPDATE・DELETE の剥奪
-- （データモデル「追記専用」、運用要件 DA-02、ADR-007）。マイグレーションのたびに、表の作成と同じ配備で動く。
-- アプリケーション利用者は db:init（運用準備）で作る。利用者がない環境（ローカルの PostgreSQL など）では何もしない。
-- 新しい表やスキーマを足したら、同じ変更でこのファイルも更新する。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${appUser}') THEN
        GRANT USAGE ON SCHEMA quotation, identity, platform TO ${appUser};
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA quotation, identity, platform TO ${appUser};

        -- 追記専用の表: INSERT・SELECT だけを許す
        REVOKE UPDATE, DELETE ON quotation.transport_request_version FROM ${appUser};
    END IF;
END
$$;
