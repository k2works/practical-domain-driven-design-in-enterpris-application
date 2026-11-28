-- Bolt 40-17（W8 の週全体レビュー A）。システム起票（DE-13 の拒否・DE-32）の冪等を、内容の文字列でなく起票元キーの一意制約で守る。
-- 利用者の起票（画面の操作）は起票元キーを持たない（NULL。一意制約は NULL の重なりを許す）。H2 と PostgreSQL の共通の構文（ADR-007）。
ALTER TABLE tracking.service_case ADD COLUMN origin_ref VARCHAR(200);

CREATE UNIQUE INDEX ux_service_case_origin_ref ON tracking.service_case (origin_ref);

COMMENT ON COLUMN tracking.service_case.origin_ref IS '起票元キー（システム起票の冪等。DE-13 の拒否した出典、DE-32 の経路設計案件と理由と時刻。利用者の起票は空）';
