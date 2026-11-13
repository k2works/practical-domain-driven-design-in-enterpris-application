package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 営業日カレンダーの表（`tracking.business_calendar`）の MyBatis のマッパー（Bolt 40-4）。読むだけ（登録は運用タスク）。
 */
@Mapper
public interface BusinessCalendarMapper {

    List<BusinessDayRow> findBetween(
            @Param("fromInclusive") LocalDate fromInclusive, @Param("toInclusive") LocalDate toInclusive);
}
