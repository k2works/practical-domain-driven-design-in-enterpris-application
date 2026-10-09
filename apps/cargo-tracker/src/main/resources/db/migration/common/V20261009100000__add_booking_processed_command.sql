-- 予約コンテキスト（booking）。Bolt 24（US-04 AC4、B-INV-03・B-INV-11、データモデル「冪等性」と `booking` の制約）。
-- 1. 貨物予約に見積り番号の写しを足し、業務番号と見積り番号で同じ見積りの予約を引けるようにする（見積りの公開 API に照会する前に
--    確定済みを判定する。失効・置換の後は見積り ID が返らないため）。業務番号の写しと同じく見積りの値の写し（R-31）。
-- 2. 処理済みコマンドの表を新設する。本予約の確定に成功したコマンドだけを、貨物予約と同じトランザクションで記録する。

-- 1. 見積り番号の写し。既存の行（Bolt 23 から作った予約）は、予約に使った見積りの見積り番号で埋める。
--    見積りの表を読むのはこの埋め込みの 1 回だけで、外部キーは張らない（スキーマの所有。ADR-001）
ALTER TABLE booking.booking ADD COLUMN quotation_no INTEGER;

-- H2 と PostgreSQL の両方で流れるよう、UPDATE ... FROM でなく相関副問合せにする
UPDATE booking.booking b
   SET quotation_no = (SELECT q.quotation_no FROM quotation.quotation q WHERE q.id = b.quotation_id);

ALTER TABLE booking.booking ALTER COLUMN quotation_no SET NOT NULL;
ALTER TABLE booking.booking ADD CONSTRAINT ck_booking_quotation_no CHECK (quotation_no >= 1);
-- 1 つの見積り（業務番号と見積り番号）から貨物予約は 1 件（B-INV-11。見積り ID の UK と同じ事実を業務の鍵で引くための索引を兼ねる）
ALTER TABLE booking.booking
    ADD CONSTRAINT uk_booking_transport_request_quotation UNIQUE (transport_request_number, quotation_no);

COMMENT ON COLUMN booking.booking.quotation_no IS '見積り番号（見積りの写し）';

-- 2. 処理済みコマンド。追記専用で、UPDATE・DELETE の権限は afterMigrate で外す（表の定義の印）
CREATE TABLE booking.processed_command (
    command_id   UUID                     NOT NULL,
    command_type VARCHAR(100)             NOT NULL,
    payload_hash VARCHAR(64)              NOT NULL,
    result_ref   VARCHAR(200),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_command PRIMARY KEY (command_id),
    CONSTRAINT ck_processed_command_type CHECK (command_type IN ('ConfirmBooking'))
);

COMMENT ON TABLE booking.processed_command IS '処理済みコマンド [append-only]';
COMMENT ON COLUMN booking.processed_command.command_id IS 'コマンド ID';
COMMENT ON COLUMN booking.processed_command.command_type IS 'コマンドの種類';
COMMENT ON COLUMN booking.processed_command.payload_hash IS '内容の照合値（業務番号・見積り番号・操作者の SHA-256）';
COMMENT ON COLUMN booking.processed_command.result_ref IS '結果の参照（予約 ID:追跡番号）';
COMMENT ON COLUMN booking.processed_command.processed_at IS '処理時刻';
