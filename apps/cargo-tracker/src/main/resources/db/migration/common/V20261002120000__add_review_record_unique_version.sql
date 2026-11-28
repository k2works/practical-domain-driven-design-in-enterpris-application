-- 1 つの版に審査の判断は 1 つ（差し戻すと新しい版ができるため）。いまは楽観ロックで守っているが、
-- 更新の経路を通らない書き込み（移行や一括の処理）でも崩れないよう、DB でも最後に止める（Bolt 5 レビュー R-02）。
ALTER TABLE quotation.review_record
    ADD CONSTRAINT uk_review_record_version UNIQUE (transport_request_id, version_no);

-- 受付一覧（審査中の一覧）の状態の絞り込みに効く索引（既存の索引は荷主企業が先頭のため使えない。R-09）
CREATE INDEX ix_transport_request_status ON quotation.transport_request (status);
