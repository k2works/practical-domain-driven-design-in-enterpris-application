-- Spring Session JDBC の session の表（spring-session-jdbc 4.1.1 の schema-h2.sql に従い、platform スキーマに置く。Bolt 14、ADR-012）。
-- 無操作 30 分の期限は MAX_INACTIVE_INTERVAL で、発行から 8 時間の上限は属性に置いた認証時刻で判定する
CREATE TABLE platform.spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT   NOT NULL,
    last_access_time      BIGINT   NOT NULL,
    max_inactive_interval INT      NOT NULL,
    expiry_time           BIGINT   NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON platform.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON platform.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON platform.spring_session (principal_name);

CREATE TABLE platform.spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    LONGVARBINARY        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES platform.spring_session (primary_id) ON DELETE CASCADE
);
