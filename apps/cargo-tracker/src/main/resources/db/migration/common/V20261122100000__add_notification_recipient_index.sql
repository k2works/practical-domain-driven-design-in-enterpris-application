-- Bolt 43-3（US-22 AC4。荷主の通知一覧 C-16）。宛先の利用者の通知を新しい順に引く索引。既存の一意制約（契機の ID、宛先）は契機の ID が先頭なので、
-- 宛先で引くと全件を読む。新しい列・表はない。H2 と PostgreSQL の共通の構文（ADR-007）。
CREATE INDEX ix_notification_recipient ON notification.notification (recipient_user_id, created_at);
