package com.example.cargotracker.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

/**
 * KPI 計測記録の MyBatis マッパー。SQL は同じパッケージの KpiObservationMapper.xml に置く。
 */
@Mapper
public interface KpiObservationMapper {

    void insertIfAbsent(KpiObservationRow row);

    /** 最初の提示時刻がないか、渡した時刻のほうが早いときだけ書く（KPI-INV-01。Bolt 21）。 */
    void updateFirstPresentedAtIfEarlier(KpiObservationRow row);

    Optional<KpiObservationRow> selectByTransportRequestId(UUID transportRequestId);

    List<KpiObservationRow> selectAll();
}
