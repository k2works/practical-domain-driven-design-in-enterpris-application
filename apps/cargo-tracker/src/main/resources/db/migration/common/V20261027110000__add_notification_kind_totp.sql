-- 通知の種類に TOTP の解除・登録・再登録のお知らせを足す（Bolt 38d。US-18 AC9、SEC-10、ADR-017）。契機（event_id）と対象（target_ref）は
-- 監査記録の ID で、本文は監査の日時から再現できる（リンクを持たないので、PASSWORD_RESET と USER_INVITATION の例外に入れない）。CHECK の
-- 差し替えは前例（V20261026100000）と同じく DROP と ADD を別の文にする。Bolt 38c2 の V20261027100000 の後に置く。
ALTER TABLE notification.notification DROP CONSTRAINT ck_notification_kind;

ALTER TABLE notification.notification
    ADD CONSTRAINT ck_notification_kind CHECK (kind IN (
        'QUOTATION_PRESENTED', 'ROUTE_APPROVED', 'QUOTATION_EXPIRING', 'BOOKING_CONFIRMED', 'TRACKING_UNDER_REVIEW',
        'CONSIGNEE_INVITATION', 'PASSWORD_RESET', 'USER_INVITATION', 'TOTP_RESET_BY_ADMIN', 'TOTP_ENROLLED',
        'TOTP_RE_ENROLLED'));

COMMENT ON COLUMN notification.notification.event_id IS '契機のイベントの ID（再設定の依頼、利用者の登録、TOTP の解除・登録・再登録の監査記録など）';
COMMENT ON COLUMN notification.notification.target_ref IS '対象の参照（再設定と利用者の招待はトークンの ID で本文は再現しない。TOTP のお知らせは監査記録の ID）';
