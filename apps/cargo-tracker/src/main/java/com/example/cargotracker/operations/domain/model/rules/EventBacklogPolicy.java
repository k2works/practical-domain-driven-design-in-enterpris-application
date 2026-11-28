package com.example.cargotracker.operations.domain.model.rules;

import com.example.cargotracker.operations.domain.model.valueobjects.BacklogSeverity;
import com.example.cargotracker.operations.domain.model.valueobjects.EventBacklog;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * OBS-03 の判定。発行から 5 分以上の未完了が 1 件以上で警告、10 件以上か最も古いものが 30 分以上で重大（非機能要件の提案の値）。
 */
@DomainRule
public final class EventBacklogPolicy {

    /** この時間以上完了しない発行を滞留とみなす。 */
    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private static final int CRITICAL_COUNT = 10;

    private static final Duration CRITICAL_AGE = Duration.ofMinutes(30);

    private EventBacklogPolicy() {}

    /**
     * @param publishedAt 未完了の発行の時刻
     * @param now 判定の時刻
     * @return 滞留
     */
    public static EventBacklog assess(List<Instant> publishedAt, Instant now) {
        List<Duration> staleAges = publishedAt.stream()
                .map(published -> Duration.between(published, now))
                .filter(age -> age.compareTo(STALE_AFTER) >= 0)
                .toList();
        Duration oldest = staleAges.stream().max(Duration::compareTo).orElse(Duration.ZERO);
        return new EventBacklog(staleAges.size(), oldest, severityOf(staleAges.size(), oldest));
    }

    private static BacklogSeverity severityOf(int staleCount, Duration oldest) {
        if (staleCount == 0) {
            return BacklogSeverity.NONE;
        }
        boolean critical = staleCount >= CRITICAL_COUNT || oldest.compareTo(CRITICAL_AGE) >= 0;
        return critical ? BacklogSeverity.CRITICAL : BacklogSeverity.WARNING;
    }
}
