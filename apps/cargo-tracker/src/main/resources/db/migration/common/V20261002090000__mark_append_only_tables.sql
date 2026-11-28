-- 追記専用の表に印を付ける（データモデル「追記専用」）。PostgreSQL の afterMigrate のコールバックが、
-- この印の付いた表からアプリケーション利用者の UPDATE・DELETE を外す（DA-02、ADR-007）。
-- 追記専用の表を新しく作るときは、同じマイグレーションで同じ印を付ける（印がなければ保護されないことは、
-- AppendOnlyGrantIntegrationTest が設計の一覧との一致で検出する）。
COMMENT ON TABLE quotation.transport_request_version IS 'append-only';
