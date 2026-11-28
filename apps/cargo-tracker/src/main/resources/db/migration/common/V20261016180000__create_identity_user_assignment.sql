-- 担当範囲（Bolt 34b、US-16 AC2、データモデル「アクセス・監査」の user_assignment）。
-- Bolt 34b はシステム管理者の管理範囲（scope_kind = COMPANY。IA-INV-14）だけを使い、業務担当者の担当範囲（IA-INV-02）と BOOKING は W6。
-- 割り当ては運用の手順で入れる（画面は W6。2026-10-16、human:kakimomokuri）ので、granted_by は NULL を許す（user_role と同じ）。
-- scope_id に企業への外部キーは張らない（BOOKING の行は予約 ID を持つ。既存の表の企業 ID に外部キーを張らない方針とも同じ）。
CREATE TABLE identity.user_assignment (
    user_id    UUID                     NOT NULL,
    scope_kind VARCHAR(30)              NOT NULL,
    scope_id   UUID                     NOT NULL,
    granted_by UUID,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_assignment PRIMARY KEY (user_id, scope_kind, scope_id),
    CONSTRAINT fk_user_assignment_user FOREIGN KEY (user_id) REFERENCES identity.app_user (id),
    CONSTRAINT ck_user_assignment_scope_kind CHECK (scope_kind IN ('COMPANY', 'BOOKING'))
);

COMMENT ON TABLE identity.user_assignment IS '担当範囲';
COMMENT ON COLUMN identity.user_assignment.user_id IS '利用者 ID';
COMMENT ON COLUMN identity.user_assignment.scope_kind IS '範囲の種類（COMPANY: 企業、BOOKING: 案件）';
COMMENT ON COLUMN identity.user_assignment.scope_id IS '範囲の ID（COMPANY なら企業 ID）';
COMMENT ON COLUMN identity.user_assignment.granted_by IS '割り当てた操作者（運用の手順で入れた行は null）';
COMMENT ON COLUMN identity.user_assignment.granted_at IS '割り当てた時刻';
