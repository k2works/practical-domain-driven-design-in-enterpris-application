package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import java.time.LocalDate;

/**
 * 営業日カレンダーのリポジトリ（送信ポート。Bolt 40-4）。カレンダーの登録は運用タスク（W10 の {@code calendar:register}）が行い、アプリケーションは読むだけ。
 */
public interface BusinessCalendarRepository {

    /**
     * 期間のカレンダーを読む（両端を含む）。
     *
     * @param fromInclusive 最初の日
     * @param toInclusive 最後の日
     * @return 登録された日だけを持つカレンダー（登録のない日は持たない。その日にかかる計算が失敗する）
     * @throws BusinessCalendarNotRegisteredException 期間に 1 日も登録がない
     */
    BusinessCalendar load(LocalDate fromInclusive, LocalDate toInclusive);
}
