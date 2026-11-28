-- Bolt 39f の開始準備の決定 (2)・(3)。確定した経路の航海番号からの照会の索引と、確定の区間の寄港の位置を足す。
-- 位置は port_call.call_no（1 始まりの運航の順）の値を写す（port_call は更新のたびに入れ直すので外部キーにしない）。NULL は Bolt 39f より前に算出した候補だけで、埋め直さない。
-- H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE routing.candidate_leg ADD COLUMN load_call_no INTEGER;
ALTER TABLE routing.candidate_leg ADD COLUMN discharge_call_no INTEGER;
ALTER TABLE routing.candidate_leg ADD CONSTRAINT ck_candidate_leg_call_no CHECK (
    (load_call_no IS NULL AND discharge_call_no IS NULL)
    OR (load_call_no IS NOT NULL AND discharge_call_no IS NOT NULL AND load_call_no < discharge_call_no)
);

CREATE INDEX ix_candidate_leg_voyage ON routing.candidate_leg (voyage_number);

COMMENT ON COLUMN routing.candidate_leg.load_call_no IS '積地の寄港の位置（航海の運航の順で 1 始まり。Bolt 39f より前の候補は NULL）';
COMMENT ON COLUMN routing.candidate_leg.discharge_call_no IS '揚地の寄港の位置（航海の運航の順で 1 始まり。Bolt 39f より前の候補は NULL）';
