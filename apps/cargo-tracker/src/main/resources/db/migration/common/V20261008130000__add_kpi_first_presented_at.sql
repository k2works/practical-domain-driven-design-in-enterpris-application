-- KPI 計測記録の最初の提示時刻（Bolt 21、US-21 AC1、KPI-INV-01・KPI-INV-03、データモデル `kpi_observation`）。
-- DE-03 で、届いた提示時刻のほうが早いときだけ書く条件付きの更新にする（再見積りの提示では変えず、届く順の入れ替わりだけ書き換える）。
-- 最初の提示時刻は提出時刻より前にならない。
ALTER TABLE identity.kpi_observation ADD COLUMN first_presented_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE identity.kpi_observation ADD CONSTRAINT ck_kpi_observation_first_presented_at
    CHECK (first_presented_at IS NULL OR first_presented_at >= submitted_at);

COMMENT ON COLUMN identity.kpi_observation.first_presented_at IS '最初の提示時刻';
