-- 現在状態の根拠の実績番号（Bolt 26c、Bolt 26b の P-10）。H2 では DEFERRABLE の外部キーを同じ書き方で作れないため、PostgreSQL だけに置く
-- （見積りの置換先 fk_quotation_replaced_by と同じ決め方）。H2 の開発・単体の検査では、集約の不変条件（実績番号は 1 からの連番、根拠は
-- 採用済みの実績）で守る。

-- 主要実績の登録は、楽観ロックの行のロックを先に取るため追跡記録を先に更新し、主要実績を後に追加するので、存在の確認はコミットの時に行う
ALTER TABLE tracking.tracking_record ADD CONSTRAINT fk_tracking_record_status_basis
    FOREIGN KEY (tracking_number, status_basis_milestone_no) REFERENCES tracking.milestone (tracking_number, milestone_no)
    DEFERRABLE INITIALLY DEFERRED;
