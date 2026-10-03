-- 業務の表と列に日本語名をコメントで付ける（データモデル「物理名」）。ER 図（SchemaSpy）と DB の定義に日本語名が出る。
-- 表の日本語名はデータモデルの ER 図、列の日本語名はドメインモデルの用語集に合わせる。
-- 追記専用の表は、日本語名の後ろに印（ [append-only]）を付ける。PostgreSQL の afterMigrate のコールバックが
-- この印で表を選び、アプリケーション利用者の UPDATE・DELETE を外す（DA-02、ADR-007）。印を消すと保護が外れる。
-- 新しい表を作るときは、同じマイグレーションで表と列に日本語のコメントを付ける（SchemaCommentIntegrationTest）。

-- KPI 計測記録
COMMENT ON TABLE identity.kpi_observation IS 'KPI 計測記録';
COMMENT ON COLUMN identity.kpi_observation.transport_request_id IS '輸送要求 ID';
COMMENT ON COLUMN identity.kpi_observation.transport_request_number IS '業務番号';
COMMENT ON COLUMN identity.kpi_observation.shipper_company_id IS '荷主企業 ID';
COMMENT ON COLUMN identity.kpi_observation.submitted_at IS '提出時刻（KPI-01 の開始）';
COMMENT ON COLUMN identity.kpi_observation.excluded IS '集計からの除外';

-- 輸送要求
COMMENT ON TABLE quotation.transport_request IS '輸送要求';
COMMENT ON COLUMN quotation.transport_request.id IS '輸送要求 ID';
COMMENT ON COLUMN quotation.transport_request.request_number IS '業務番号';
COMMENT ON COLUMN quotation.transport_request.shipper_company_id IS '荷主企業 ID';
COMMENT ON COLUMN quotation.transport_request.status IS '状態';
COMMENT ON COLUMN quotation.transport_request.current_version_no IS '現在の版番号';
COMMENT ON COLUMN quotation.transport_request.version IS '楽観ロックの版（業務の版番号とは別）';

-- 業務番号の採番
COMMENT ON TABLE quotation.transport_request_number_counter IS '業務番号の採番';
COMMENT ON COLUMN quotation.transport_request_number_counter.number_year IS '業務番号の年';
COMMENT ON COLUMN quotation.transport_request_number_counter.last_no IS '最後に振った連番';

-- 輸送要求版（追記専用）
COMMENT ON TABLE quotation.transport_request_version IS '輸送要求版 [append-only]';
COMMENT ON COLUMN quotation.transport_request_version.transport_request_id IS '輸送要求 ID';
COMMENT ON COLUMN quotation.transport_request_version.version_no IS '版番号';
COMMENT ON COLUMN quotation.transport_request_version.consignee_company_id IS '荷受人企業 ID';
COMMENT ON COLUMN quotation.transport_request_version.origin_unlocode IS '出発地（UN/LOCODE）';
COMMENT ON COLUMN quotation.transport_request_version.destination_unlocode IS '目的地（UN/LOCODE）';
COMMENT ON COLUMN quotation.transport_request_version.arrival_deadline IS '希望到着期限';
COMMENT ON COLUMN quotation.transport_request_version.cargo_category IS '貨物種別';
COMMENT ON COLUMN quotation.transport_request_version.package_type IS '荷姿';
COMMENT ON COLUMN quotation.transport_request_version.package_count IS '個数';
COMMENT ON COLUMN quotation.transport_request_version.gross_weight_kg IS '総重量（kg）';
COMMENT ON COLUMN quotation.transport_request_version.volume_m3 IS '容積（m3）';
COMMENT ON COLUMN quotation.transport_request_version.submitted_by IS '提出者';
COMMENT ON COLUMN quotation.transport_request_version.submitted_at IS '提出時刻';

-- 審査記録（追記専用）
COMMENT ON TABLE quotation.review_record IS '審査記録 [append-only]';
COMMENT ON COLUMN quotation.review_record.id IS '審査記録 ID';
COMMENT ON COLUMN quotation.review_record.transport_request_id IS '輸送要求 ID';
COMMENT ON COLUMN quotation.review_record.version_no IS '審査した版番号';
COMMENT ON COLUMN quotation.review_record.decision IS '判断（確定・差戻し）';
COMMENT ON COLUMN quotation.review_record.reviewer_id IS '判断者';
COMMENT ON COLUMN quotation.review_record.rationale IS '確定の根拠・差戻しの理由';
COMMENT ON COLUMN quotation.review_record.missing_items IS '不足事項';
COMMENT ON COLUMN quotation.review_record.decided_at IS '判断の時刻';

-- 必要書類（追記専用）
COMMENT ON TABLE quotation.required_document IS '必要書類 [append-only]';
COMMENT ON COLUMN quotation.required_document.transport_request_id IS '輸送要求 ID';
COMMENT ON COLUMN quotation.required_document.version_no IS '版番号';
COMMENT ON COLUMN quotation.required_document.document_no IS '書類番号';
COMMENT ON COLUMN quotation.required_document.document_type IS '書類の種類';
COMMENT ON COLUMN quotation.required_document.file_name IS 'ファイル名（画面の表示用）';
COMMENT ON COLUMN quotation.required_document.media_type IS '形式（PDF・PNG・JPEG）';
COMMENT ON COLUMN quotation.required_document.size_bytes IS '大きさ（バイト）';
COMMENT ON COLUMN quotation.required_document.sha256 IS '中身の SHA-256';
COMMENT ON COLUMN quotation.required_document.object_key IS '保存先のキー';
