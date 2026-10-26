-- 通知の種類に利用者の招待を足す（Bolt 38b2b。US-16 AC1、ADR-017）。システム管理者が登録した利用者への初回の password の設定のリンク。
-- 対象（target_ref）は招待のトークンの ID で、本文は再現しない（PASSWORD_RESET と同じ例外）。CHECK の差し替えは前例
-- （V20261008100000）と同じく DROP と ADD を別の文にする。
ALTER TABLE notification.notification DROP CONSTRAINT ck_notification_kind;

ALTER TABLE notification.notification
    ADD CONSTRAINT ck_notification_kind CHECK (kind IN (
        'QUOTATION_PRESENTED', 'ROUTE_APPROVED', 'QUOTATION_EXPIRING', 'BOOKING_CONFIRMED', 'TRACKING_UNDER_REVIEW',
        'CONSIGNEE_INVITATION', 'PASSWORD_RESET', 'USER_INVITATION'));

COMMENT ON COLUMN notification.notification.event_id IS '契機のイベントの ID（再設定の依頼、利用者の登録など）';
COMMENT ON COLUMN notification.notification.target_ref IS '対象の参照（再設定と利用者の招待はトークンの ID。本文は再現しない）';
