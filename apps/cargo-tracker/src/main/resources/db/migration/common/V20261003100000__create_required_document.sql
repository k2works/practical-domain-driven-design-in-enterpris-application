-- 必要書類（Bolt 7、US-01 AC2、Q-INV-16、データモデル `required_document`）。
-- 版に付く書類の事実で、後から変えないため追記専用にする（2026-10-03 に承認）。
-- ファイルの中身は書類の保存（本番は S3、開発はローカルのファイルシステム）に置き、ここにはオブジェクトキーと SHA-256 を持つ。
-- 出し直しでは前の版の行を、同じ object_key で新しい版に INSERT する（ファイルを複製しない）。
CREATE TABLE quotation.required_document (
    transport_request_id UUID         NOT NULL,
    version_no           INTEGER      NOT NULL,
    document_no          INTEGER      NOT NULL,
    document_type        VARCHAR(30)  NOT NULL,
    file_name            VARCHAR(255) NOT NULL,
    media_type           VARCHAR(30)  NOT NULL,
    size_bytes           BIGINT       NOT NULL,
    sha256               CHAR(64)     NOT NULL,
    object_key           VARCHAR(500) NOT NULL,
    CONSTRAINT pk_required_document PRIMARY KEY (transport_request_id, version_no, document_no),
    CONSTRAINT fk_required_document_version
        FOREIGN KEY (transport_request_id, version_no)
        REFERENCES quotation.transport_request_version (transport_request_id, version_no),
    CONSTRAINT ck_required_document_type CHECK (document_type IN ('COMMERCIAL_INVOICE', 'PACKING_LIST', 'OTHER')),
    CONSTRAINT ck_required_document_media_type CHECK (media_type IN ('PDF', 'PNG', 'JPEG')),
    CONSTRAINT ck_required_document_size CHECK (size_bytes BETWEEN 1 AND 10485760)
);

COMMENT ON TABLE quotation.required_document IS 'append-only';
