-- 企業・利用者・役割・監査記録（Bolt 14、US-18 の password によるログイン、データモデル「アクセス・監査」）。
-- TOTP とロックの列（totp_secret_encrypted・failed_attempts・locked_until）は W5、監査記録の対象・変更前後などの列は使う Bolt で足す。
-- 既存の表の企業 ID・利用者 ID には外部キーを張らない（仮の荷受人の企業を表に持たないため。企業マスター US-16 で決める）。
CREATE TABLE identity.company (
    id         UUID                     NOT NULL,
    name       VARCHAR(200)             NOT NULL,
    kind       VARCHAR(30)              NOT NULL,
    active     BOOLEAN                  NOT NULL,
    version    BIGINT                   NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_company PRIMARY KEY (id)
);

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
    CONSTRAINT fk_app_user_company FOREIGN KEY (company_id) REFERENCES identity.company (id)
);

CREATE TABLE identity.user_role (
    user_id    UUID                     NOT NULL,
    role       VARCHAR(30)              NOT NULL,
    granted_by UUID,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES identity.app_user (id)
);

CREATE TABLE identity.audit_record (
    id               UUID                     NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id    UUID,
    actor_company_id UUID,
    action           VARCHAR(100)             NOT NULL,
    result           VARCHAR(30)              NOT NULL,
    reason           VARCHAR(100),
    correlation_id   VARCHAR(100),
    CONSTRAINT pk_audit_record PRIMARY KEY (id)
);
