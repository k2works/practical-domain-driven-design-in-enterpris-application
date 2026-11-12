-- Bolt 39g4c の開始準備の決定 (3)（確認ポイント CP-3・CP-6）。最新の到着見込みの根拠（【業務の判断】(aw)、台帳 D-139）を見込みと一緒に保存する 2 列を足す。
-- 種類は ORIGINAL_ETA（当初の到着予定）・MILESTONE（採用の実績）・VOYAGE_ARRIVAL（最後の区間の航海の到着予定）で、実績のときだけ実績番号を持つ。追跡の開始の
-- INSERT は ORIGINAL_ETA と NULL を書き、主要実績の UPDATE（DE-12 の受け取りを含む）が 2 列を書く。同じ行の列どうしで見込みと根拠の食い違いを拒む（CHECK。
-- H2 と PostgreSQL の両方）。根拠の実績番号の外部キーは PostgreSQL だけ（V20261115100100）。既存の行は 2 列とも NULL のままで補正しない（次に見込みを導き直すと
-- 埋まる。Release 1.0 のパイロットの前で業務のデータはない）。H2 と PostgreSQL の共通の構文で書く（ADR-007）。
ALTER TABLE tracking.tracking_record ADD COLUMN latest_eta_basis VARCHAR(30);
ALTER TABLE tracking.tracking_record ADD COLUMN latest_eta_basis_milestone_no INTEGER;

-- 種類が NULL の行で比べの結果が NULL（CHECK を通る）にならないよう、2 つ目の枝は種類が NOT NULL のときだけにする
ALTER TABLE tracking.tracking_record ADD CONSTRAINT ck_tracking_record_latest_eta_basis CHECK (
    (latest_eta_basis IS NULL AND latest_eta_basis_milestone_no IS NULL)
    OR (latest_eta_basis IS NOT NULL AND (
        (latest_eta_basis = 'ORIGINAL_ETA' AND latest_eta_basis_milestone_no IS NULL AND latest_eta = original_eta)
        OR (latest_eta_basis = 'MILESTONE' AND latest_eta_basis_milestone_no IS NOT NULL)
        OR (latest_eta_basis = 'VOYAGE_ARRIVAL' AND latest_eta_basis_milestone_no IS NULL
            AND voyage_arrival_at IS NOT NULL AND voyage_arrival_at = latest_eta))));

COMMENT ON COLUMN tracking.tracking_record.latest_eta_basis IS '見込みの根拠';
COMMENT ON COLUMN tracking.tracking_record.latest_eta_basis_milestone_no IS '見込みの根拠の実績番号';
