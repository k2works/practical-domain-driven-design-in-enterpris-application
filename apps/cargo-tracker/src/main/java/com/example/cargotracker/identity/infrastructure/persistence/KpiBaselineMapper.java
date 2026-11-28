package com.example.cargotracker.identity.infrastructure.persistence;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * KPI 基準値の MyBatis マッパー（追記専用。W9 の Bolt 44-2）。SQL は同じパッケージの KpiBaselineMapper.xml に置く。
 */
@Mapper
public interface KpiBaselineMapper {

    void insert(KpiBaselineRow row);

    /** 指定の KPI の最新の基準値（ほかの記録に訂正されていないもの）。 */
    Optional<KpiBaselineRow> selectLatest(@Param("kpi") String kpi);
}
