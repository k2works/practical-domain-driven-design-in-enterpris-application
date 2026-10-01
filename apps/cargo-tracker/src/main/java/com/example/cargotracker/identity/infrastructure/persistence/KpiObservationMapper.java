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

    void insert(KpiObservationRow row);

    Optional<KpiObservationRow> selectByTransportRequestId(UUID transportRequestId);

    List<KpiObservationRow> selectAll();
}
