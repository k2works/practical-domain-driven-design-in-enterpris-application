-- 確定は案件に 1 つ（R-INV-05。Bolt 37d）。H2 は部分インデックスを持たないので、開発環境とデモ環境は集約が守る。
CREATE UNIQUE INDEX ux_route_version_confirmed ON routing.route_version (routing_case_id)
    WHERE status = 'CONFIRMED';
