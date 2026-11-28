package com.example.cargotracker.identity.application.internal.outboundservices.acl;

import com.example.cargotracker.tracking.interfaces.api.InquiryCounts;
import com.example.cargotracker.tracking.interfaces.api.InquiryStatistics;
import java.time.Instant;

/**
 * 追跡の期間の照会の件数。腐敗防止層で、追跡の公開 API（{@link InquiryStatistics}）を呼び、結果の型をこの層の外に出さない（ADR-017。US-21 AC4。
 * W9 の Bolt 44-1）。
 */
public class TrackingInquiryStatistics {

    private final InquiryStatistics inquiryStatistics;

    public TrackingInquiryStatistics(InquiryStatistics inquiryStatistics) {
        this.inquiryStatistics = inquiryStatistics;
    }

    /**
     * 受付時刻が範囲にある照会の件数。
     *
     * @param fromInclusive この時刻以後
     * @param toExclusive この時刻より前
     * @return 照会総数と、顧客 Web で完結した照会数（この層の値）
     */
    public Counts countBetween(Instant fromInclusive, Instant toExclusive) {
        InquiryCounts counts = inquiryStatistics.countBetween(fromInclusive, toExclusive);
        return new Counts(counts.total(), counts.selfServed());
    }

    /**
     * 期間の照会の件数（この層の値）。
     *
     * @param total 照会総数
     * @param selfServed 顧客 Web で完結した照会数
     */
    public record Counts(long total, long selfServed) {}
}
