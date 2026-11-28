-- 定期処理の排他（ShedLock。W10 の Bolt 53-1。データモデル「platform」）。複数のインスタンスが同じ定期処理を同時に動かさないための
-- ロックの表。ShedLock の JdbcTemplateLockProvider の定義（name が主キー）に合わせる。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
-- 時刻はデータベースの時刻で数える（usingDbTime）ので、インスタンスの時計のずれに左右されない。
CREATE TABLE platform.shedlock (
    name       VARCHAR(64)              NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255)             NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

COMMENT ON TABLE platform.shedlock IS '定期処理のロック（ShedLock）。1 つの名前を同時に 1 インスタンスだけが持つ';
COMMENT ON COLUMN platform.shedlock.name IS 'ロックの名前（定期処理ごと）';
COMMENT ON COLUMN platform.shedlock.lock_until IS 'このロックを持てる期限（超えたら別のインスタンスが取れる）';
COMMENT ON COLUMN platform.shedlock.locked_at IS 'ロックを取った時刻';
COMMENT ON COLUMN platform.shedlock.locked_by IS 'ロックを取ったインスタンス（ホスト名）';
