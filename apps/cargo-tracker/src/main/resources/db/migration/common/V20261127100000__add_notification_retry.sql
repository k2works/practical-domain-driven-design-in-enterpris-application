-- Bolt 61（ADR-021 の C-3。W10-1 計画 CP-5。2026-11-27、human:kakimomokuri が承認）。失敗した荷主への業務の通知の再送の列。
-- 既存の失敗した行は retryable が NULL のままで、再送しない。H2 と PostgreSQL の共通の構文（ADR-007）。
ALTER TABLE notification.notification ADD COLUMN link VARCHAR(500);

ALTER TABLE notification.notification ADD COLUMN retry_count INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE notification.notification ADD COLUMN retryable BOOLEAN;

-- リンクを保存できるのは、リンクにトークンを含まない荷主への業務の通知だけ（招待・password の再設定のリンクは書けない）
ALTER TABLE notification.notification
    ADD CONSTRAINT ck_notification_link_kind CHECK (link IS NULL OR kind IN (
        'QUOTATION_PRESENTED', 'ROUTE_APPROVED', 'QUOTATION_EXPIRING', 'BOOKING_CONFIRMED', 'TRACKING_UNDER_REVIEW'));

ALTER TABLE notification.notification
    ADD CONSTRAINT ck_notification_retry_count CHECK (retry_count BETWEEN 0 AND 3);

-- 再送の可否は失敗した行だけが持ち、再送できる行はリンクを持つ
ALTER TABLE notification.notification
    ADD CONSTRAINT ck_notification_retryable CHECK (
        retryable IS NULL OR (status = 'FAILED' AND (retryable = FALSE OR link IS NOT NULL)));

COMMENT ON COLUMN notification.notification.link IS '画面への導線の URL（再送する種類だけ）';

COMMENT ON COLUMN notification.notification.retry_count IS '再送の回数';

COMMENT ON COLUMN notification.notification.retryable IS '再送できる失敗か（失敗の行だけ）';
