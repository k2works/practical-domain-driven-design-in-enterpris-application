package com.example.cargotracker.tracking.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 拒否した主要実績の表（`tracking.rejected_milestone`）の MyBatis のマッパー（Bolt 39g）。
 */
@Mapper
public interface RejectedMilestoneMapper {

    void insert(RejectedMilestoneRow row);

    int countBySource(@Param("sourceKind") String sourceKind, @Param("sourceRef") String sourceRef);
}
