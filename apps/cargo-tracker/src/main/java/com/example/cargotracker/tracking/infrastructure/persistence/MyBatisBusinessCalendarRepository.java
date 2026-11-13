package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.tracking.domain.model.aggregates.BusinessCalendarRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 営業日カレンダーのリポジトリの MyBatis 実装（Bolt 40-4）。登録された日だけを持つカレンダーを返し、期間に 1 日も登録がなければ登録漏れとして失敗する。
 */
@Repository
public class MyBatisBusinessCalendarRepository implements BusinessCalendarRepository {

    private final BusinessCalendarMapper mapper;

    public MyBatisBusinessCalendarRepository(BusinessCalendarMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public BusinessCalendar load(LocalDate fromInclusive, LocalDate toInclusive) {
        List<BusinessDay> days = mapper.findBetween(fromInclusive, toInclusive).stream()
                .map(MyBatisBusinessCalendarRepository::toDay)
                .toList();
        if (days.isEmpty()) {
            throw new BusinessCalendarNotRegisteredException(fromInclusive);
        }
        return BusinessCalendar.of(days);
    }

    private static BusinessDay toDay(BusinessDayRow row) {
        return new BusinessDay(
                row.calendarDate(),
                row.businessDay(),
                row.openTime(),
                row.closeTime(),
                ZoneId.of(row.timeZone()),
                row.note());
    }
}
