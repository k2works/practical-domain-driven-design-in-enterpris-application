package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import java.util.Optional;

/**
 * KPI 基準値のリポジトリ（送信ポート。追記専用。W9 の Bolt 44-2）。実装は infrastructure に置く。
 */
public interface KpiBaselineRepository {

    /** 基準値を追記する（更新・削除はしない。KPI-INV-02）。 */
    void save(KpiBaseline baseline);

    /** 指定の KPI の最新の基準値（ほかの記録に訂正されていないもの）。なければ空。 */
    Optional<KpiBaseline> findLatest(KpiKind kpi);
}
