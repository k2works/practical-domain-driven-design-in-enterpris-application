-- 外部データ（external_data）。Bolt 39a2（外部原本の取込、データモデル `external_data`）。
-- 保存した原本の表を新設する。外部から受け取った原本（ファイルの内容・API の応答）をそのまま残す。
-- 追記専用で、UPDATE・DELETE の権限は afterMigrate で外す（表の定義の印）。ハッシュは持たない（取込・外部記録・受信記録が持つ）。
-- W10 で原本の保存を S3 に差し替えたら使わなくなる表。
-- content の BYTEA は PostgreSQL の型で、H2 も別名として受けるので共通の置き場所に置く（Bolt 39a2 のステップ 2 で確かめた）。
CREATE SCHEMA IF NOT EXISTS external_data;

CREATE TABLE external_data.stored_payload (
    object_key VARCHAR(500)             NOT NULL,
    content    BYTEA                    NOT NULL,
    stored_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_stored_payload PRIMARY KEY (object_key)
);

COMMENT ON TABLE external_data.stored_payload IS '保存した原本 [append-only]';
COMMENT ON COLUMN external_data.stored_payload.object_key IS '原本のオブジェクトキー';
COMMENT ON COLUMN external_data.stored_payload.content IS '原本の内容';
COMMENT ON COLUMN external_data.stored_payload.stored_at IS '保存した時刻';
