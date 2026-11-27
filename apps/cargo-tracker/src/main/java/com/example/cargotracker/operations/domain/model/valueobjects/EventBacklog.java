package com.example.cargotracker.operations.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.time.Duration;
import java.util.Objects;

/**
 * 未完了のイベント配信の滞留。件数と経過は、滞留とみなす時間を超えた発行だけで数える。
 *
 * @param staleCount 滞留している発行の数
 * @param oldestAge 滞留している発行のうち最も古いものの経過（なければ 0）
 * @param severity 重要度
 */
@ValueObject
public record EventBacklog(int staleCount, Duration oldestAge, BacklogSeverity severity) {

    public EventBacklog {
        Objects.requireNonNull(oldestAge, "oldestAge");
        Objects.requireNonNull(severity, "severity");
    }
}
