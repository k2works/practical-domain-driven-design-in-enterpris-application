-- 企業・利用者・役割・監査記録（Bolt 14、US-18 の password によるログイン、データモデル「アクセス・監査」）。
-- TOTP とロックの列（totp_secret_encrypted・failed_attempts・locked_until）は W5、監査記録の対象・変更前後などの列は使う Bolt で足す。
-- 既存の表の企業 ID・利用者 ID には外部キーを張らない（仮の荷受人の企業を表に持たないため。企業マスター US-16 で決める）。
-- 開発用の企業と利用者はここに置かず、dev のときだけの場所（db/dev-data）に置く（ADR-011 の決定 3、ADR-012）。
CREATE TABLE identity.company (
    id         UUID                     NOT NULL,
    name       VARCHAR(200)             NOT NULL,
    kind       VARCHAR(30)              NOT NULL,
    active     BOOLEAN                  NOT NULL,
    version    BIGINT                   NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_company PRIMARY KEY (id),
    CONSTRAINT ck_company_kind CHECK (kind IN ('SHIPPER', 'CONSIGNEE', 'OPERATOR'))
);

-- email は前後の空白を除き小文字にして保存し、一意にする（大文字・小文字の違うメールアドレスを同じ利用者として扱う）
CREATE TABLE identity.app_user (
    id            UUID                     NOT NULL,
    company_id    UUID                     NOT NULL,
    email         VARCHAR(320)             NOT NULL,
    display_name  VARCHAR(200)             NOT NULL,
    password_hash VARCHAR(200)             NOT NULL,
    status        VARCHAR(30)              NOT NULL,
    version       BIGINT                   NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT fk_app_user_company FOREIGN KEY (company_id) REFERENCES identity.company (id),
    CONSTRAINT ux_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_normalized CHECK (email = LOWER(TRIM(email))),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX ix_app_user_company ON identity.app_user (company_id);

CREATE TABLE identity.user_role (
    user_id    UUID                     NOT NULL,
    role       VARCHAR(30)              NOT NULL,
    granted_by UUID,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES identity.app_user (id),
    CONSTRAINT ck_user_role_role CHECK (role IN ('SHIPPER', 'SALES', 'ROUTE_DESIGNER', 'TRACKING_MANAGER',
                                                 'DATA_STEWARD', 'CUSTOMER_SUPPORT', 'SYSTEM_ADMIN', 'AUDITOR'))
);

-- 認証の成功・失敗とログアウトを、同じ request の中で同期に書く（IA-INV-08）。追記だけを許す（IA-INV-07）。
-- 存在しないメールアドレスでのログインの失敗は、操作者を null にし、メールアドレスを残さない
CREATE TABLE identity.audit_record (
    id               UUID                     NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id    UUID,
    actor_company_id UUID,
    action           VARCHAR(100)             NOT NULL,
    result           VARCHAR(30)              NOT NULL,
    reason           VARCHAR(100),
    correlation_id   VARCHAR(100),
    CONSTRAINT pk_audit_record PRIMARY KEY (id),
    CONSTRAINT ck_audit_record_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_audit_record_actor ON identity.audit_record (actor_user_id, occurred_at);
CREATE INDEX ix_audit_record_occurred_at ON identity.audit_record (occurred_at);

COMMENT ON TABLE identity.company IS '企業';
COMMENT ON COLUMN identity.company.id IS '企業 ID';
COMMENT ON COLUMN identity.company.name IS '企業名';
COMMENT ON COLUMN identity.company.kind IS '種類（SHIPPER: 荷主、CONSIGNEE: 荷受人、OPERATOR: A 社）';
COMMENT ON COLUMN identity.company.active IS '有効か（無効な企業の利用者は認証できない）';
COMMENT ON COLUMN identity.company.version IS '版（楽観ロック）';
COMMENT ON COLUMN identity.company.created_at IS '作成時刻';
COMMENT ON COLUMN identity.company.updated_at IS '更新時刻';

COMMENT ON TABLE identity.app_user IS '利用者';
COMMENT ON COLUMN identity.app_user.id IS '利用者 ID';
COMMENT ON COLUMN identity.app_user.company_id IS '所属企業';
COMMENT ON COLUMN identity.app_user.email IS 'メールアドレス（前後の空白を除いた小文字。ログインの識別子）';
COMMENT ON COLUMN identity.app_user.display_name IS '表示名';
COMMENT ON COLUMN identity.app_user.password_hash IS 'password のハッシュ（{bcrypt} の接頭辞付き。SEC-07）';
COMMENT ON COLUMN identity.app_user.status IS '利用状態（ACTIVE: 利用中、SUSPENDED: 利用停止）';
COMMENT ON COLUMN identity.app_user.version IS '版（楽観ロック）';
COMMENT ON COLUMN identity.app_user.created_at IS '作成時刻';
COMMENT ON COLUMN identity.app_user.updated_at IS '更新時刻';

COMMENT ON TABLE identity.user_role IS '利用者の役割';
COMMENT ON COLUMN identity.user_role.user_id IS '利用者 ID';
COMMENT ON COLUMN identity.user_role.role IS '役割（BR-15 の 8 役割）';
COMMENT ON COLUMN identity.user_role.granted_by IS '付与した利用者（初期の登録では null）';
COMMENT ON COLUMN identity.user_role.granted_at IS '付与した時刻';

COMMENT ON TABLE identity.audit_record IS '監査記録 [append-only]';
COMMENT ON COLUMN identity.audit_record.id IS '監査記録 ID';
COMMENT ON COLUMN identity.audit_record.occurred_at IS '発生時刻';
COMMENT ON COLUMN identity.audit_record.actor_user_id IS '操作者（存在しないメールアドレスでのログインの失敗では null）';
COMMENT ON COLUMN identity.audit_record.actor_company_id IS '操作者の企業';
COMMENT ON COLUMN identity.audit_record.action IS '操作（LOGIN_SUCCEEDED・LOGIN_FAILED・LOGOUT など）';
COMMENT ON COLUMN identity.audit_record.result IS '結果（SUCCESS・FAILURE）';
COMMENT ON COLUMN identity.audit_record.reason IS '理由（ログインの失敗の理由など）';
COMMENT ON COLUMN identity.audit_record.correlation_id IS '相関 ID（同じ操作の記録をつなぐ。使う Bolt で埋める）';
