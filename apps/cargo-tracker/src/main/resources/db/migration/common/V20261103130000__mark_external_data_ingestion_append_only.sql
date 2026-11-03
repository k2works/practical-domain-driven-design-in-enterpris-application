-- 取込を追記専用にする（Bolt 39b の確認ポイント 2。【前の決定の変更】Bolt 39a3 の開始準備の決定 (4) の「追記専用は 3 表」を 4 表に変えた）。
-- 受け取った件数（received_count）は取込の行を書くときに入れ、更新しない。PostgreSQL の afterMigrate のコールバックが、
-- この印の付いた表からアプリケーション利用者の UPDATE・DELETE を外す（DA-02、ADR-007）。
COMMENT ON TABLE external_data.ingestion IS '取込 [append-only]';
