package com.example.cargotracker.externaldata.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 取込の頻度（FRS-04。承認済みの情報源の取込は 1 日 1 回以上）。「当日」は日本時間の暦の日で、土日と祝日も数える
 * （W10-1 計画 CP-6。2026-11-27、human:kakimomokuri）。
 */
@DomainRule
public final class IngestionCadence {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    private IngestionCadence() {}

    /** その時刻を含む日の始まり（日本時間の 0:00）。 */
    public static Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, BUSINESS_ZONE)
                .atStartOfDay(BUSINESS_ZONE)
                .toInstant();
    }
}
