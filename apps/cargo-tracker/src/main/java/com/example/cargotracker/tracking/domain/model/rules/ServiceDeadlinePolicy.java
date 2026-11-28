package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 有人案件の受領・回答の期限の規則（Bolt 40-4。SC-INV-04、SVC-01〜03・05）。営業日カレンダーで数え、営業時間外の受付は次に開く時刻から数える。
 *
 * <p>優先度高: 受領は {@code highReceipt}、回答は数え始めの営業日の終了（当日中）。通常: 受領は {@code normalReceipt}、回答は同じ時刻の
 * {@code normalResponseBusinessDays} 営業日後。期限の値は non_functional の SVC-01・02 で状態が「提案」なので、{@link #provisional()} の 1 か所に
 * 仮の値として置く（OQ-06 の運用測定で確定したら差し替える。W8 計画の CP-8）。
 *
 * @param highReceipt 優先度高の受領期限（営業時間）
 * @param normalReceipt 通常の受領期限（営業時間）
 * @param normalResponseBusinessDays 通常の回答期限（営業日）
 */
@DomainRule
public record ServiceDeadlinePolicy(Duration highReceipt, Duration normalReceipt, int normalResponseBusinessDays) {

    public ServiceDeadlinePolicy {
        Objects.requireNonNull(highReceipt, "highReceipt");
        Objects.requireNonNull(normalReceipt, "normalReceipt");
        if (normalResponseBusinessDays < 1) {
            throw new IllegalArgumentException("通常の回答期限は 1 営業日以上: " + normalResponseBusinessDays);
        }
    }

    /** 仮の値（SVC-01: 高は受領 1 営業時間・回答は当日中、SVC-02: 通常は受領 4 営業時間・回答は 2 営業日。提案）。 */
    public static ServiceDeadlinePolicy provisional() {
        return new ServiceDeadlinePolicy(Duration.ofHours(1), Duration.ofHours(4), 2);
    }

    /**
     * 受付の時刻から期限を計算する。
     *
     * @param priority 優先度
     * @param receivedAt 受付の時刻
     * @param calendar 営業日カレンダー
     * @throws com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException
     *     計算が登録のない日にかかる
     */
    public ServiceDeadlines deadlinesFor(ServicePriority priority, Instant receivedAt, BusinessCalendar calendar) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(calendar, "calendar");
        Instant countedFrom = calendar.openingOnOrAfter(receivedAt);
        return switch (priority) {
            case HIGH ->
                new ServiceDeadlines(
                        countedFrom,
                        calendar.addBusinessHours(countedFrom, highReceipt),
                        calendar.closeOfDayContaining(countedFrom));
            case NORMAL ->
                new ServiceDeadlines(
                        countedFrom,
                        calendar.addBusinessHours(countedFrom, normalReceipt),
                        calendar.addBusinessDays(countedFrom, normalResponseBusinessDays));
        };
    }
}
