-- 本予約前の条件の変更（Bolt 62、US-05 AC1、Q-INV-18）。状態に条件の変更で無効（VOIDED）を足す。荷主が輸送条件を変更すると、旧版の有効な
-- 見積りをこの状態にする。経路・荷主の回答・承認の列は残す（それらの制約は状態を変えても成り立つ）。置換先を持たないので、置換済み
-- （REPLACED。ck_quotation_replaced が置換先を必須にする）は使わない。部分一意インデックス ux_quotation_active は有効な状態だけを
-- 数えるので変えない。値を足すだけなので、前の版のアプリケーションは既存の行を読める（無効の行ができるのは新しい版が動いた後）。
ALTER TABLE quotation.quotation DROP CONSTRAINT ck_quotation_status;
ALTER TABLE quotation.quotation ADD CONSTRAINT ck_quotation_status
    CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PRESENTED', 'ROUTING_REQUESTED', 'AWAITING_SHIPPER_APPROVAL',
                      'APPROVED', 'EXPIRED', 'REPLACED', 'VOIDED'));
