package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.time.LocalDate;

/**
 * 営業日カレンダーに登録のない日にかかる計算（Bolt 40-4。SVC-07）。黙って平日として扱わず失敗させ、翌年分の登録漏れに気づけるようにする。
 */
public final class BusinessCalendarNotRegisteredException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** @param date 登録のない日 */
    public BusinessCalendarNotRegisteredException(LocalDate date) {
        super("営業日カレンダーに登録がない日: " + date);
    }
}
