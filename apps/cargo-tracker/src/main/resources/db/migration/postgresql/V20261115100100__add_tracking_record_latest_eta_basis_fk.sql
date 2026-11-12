-- 見込みの根拠の実績番号（Bolt 39g4c の開始準備の決定 (3)）。現在状態の根拠の実績番号（fk_tracking_record_status_basis。V20261010120100）と同じく、H2 では
-- DEFERRABLE の外部キーを同じ書き方で作れないため、PostgreSQL だけに置く。H2 の開発・単体の検査では、集約の不変条件（根拠は予定の時点に結ぶ採用の実績）で守る。

-- 主要実績の登録は、楽観ロックの行のロックを先に取るため追跡記録を先に更新し、主要実績を後に追加するので、存在の確認はコミットの時に行う
ALTER TABLE tracking.tracking_record ADD CONSTRAINT fk_tracking_record_latest_eta_basis
    FOREIGN KEY (tracking_number, latest_eta_basis_milestone_no) REFERENCES tracking.milestone (tracking_number, milestone_no)
    DEFERRABLE INITIALLY DEFERRED;
