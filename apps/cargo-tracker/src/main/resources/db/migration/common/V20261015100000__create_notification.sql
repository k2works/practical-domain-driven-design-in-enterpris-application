-- 通知コンテキスト（notification）。Bolt 33（ADR-017、N-INV-01〜03、データモデル `notification`）。
-- 利用者へ送るメール 1 通の宛先・種類・対象・送信の結果を記録する。本文とリンクは保存しない（N-INV-01）。
-- Bolt 33 は password の再設定（PASSWORD_RESET）だけを使う。種類の CHECK には W9 の荷主への業務の通知の値も先に置く。
-- 宛先の利用者はアクセス・監査の表なので外部キーを張らない（スキーマの所有。ADR-001）。行はシステムが作るので、監査用の作成者・更新者の
-- 列を持たない。状態は PENDING を条件にした 1 文の更新で書くので、版の列を持たない（データモデルの例外）。
CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notification (
    id                UUID                     NOT NULL,
    event_id          UUID                     NOT NULL,
    recipient_user_id UUID                     NOT NULL,
    email             VARCHAR(320)             NOT NULL,
    kind              VARCHAR(30)              NOT NULL,
    target_ref        VARCHAR(200)             NOT NULL,
    locale            VARCHAR(10)              NOT NULL,
    status            VARCHAR(30)              NOT NULL,
    sent_at           TIMESTAMP WITH TIME ZONE,
    failure_reason    VARCHAR(1000),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    -- N-INV-02: 同じ契機と宛先に 1 通。再配信・同時の要求の冪等の正
    CONSTRAINT uk_notification_event_recipient UNIQUE (event_id, recipient_user_id),
    CONSTRAINT ck_notification_kind CHECK (kind IN (
        'QUOTATION_PRESENTED', 'ROUTE_APPROVED', 'QUOTATION_EXPIRING', 'BOOKING_CONFIRMED', 'TRACKING_UNDER_REVIEW',
        'CONSIGNEE_INVITATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    -- 送信時刻は送信済みだけ、失敗の理由は失敗だけが持つ
    CONSTRAINT ck_notification_sent_at CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT ck_notification_failure_reason CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL))
);

COMMENT ON TABLE notification.notification IS '通知';
COMMENT ON COLUMN notification.notification.id IS '通知 ID';
COMMENT ON COLUMN notification.notification.event_id IS '契機の ID（password の再設定では依頼の ID）';
COMMENT ON COLUMN notification.notification.recipient_user_id IS '宛先の利用者 ID（アクセス・監査の利用者。外部キーを張らない）';
COMMENT ON COLUMN notification.notification.email IS '宛先のメールアドレス';
COMMENT ON COLUMN notification.notification.kind IS '通知の種類';
COMMENT ON COLUMN notification.notification.target_ref IS '対象の参照（password の再設定では再設定のトークンの ID。本文は再現しない）';
COMMENT ON COLUMN notification.notification.locale IS '文面の言語';
COMMENT ON COLUMN notification.notification.status IS '通知の状態';
COMMENT ON COLUMN notification.notification.sent_at IS '送信時刻';
COMMENT ON COLUMN notification.notification.failure_reason IS '失敗の理由（リンクを含まない）';
COMMENT ON COLUMN notification.notification.created_at IS '記録した時刻';
