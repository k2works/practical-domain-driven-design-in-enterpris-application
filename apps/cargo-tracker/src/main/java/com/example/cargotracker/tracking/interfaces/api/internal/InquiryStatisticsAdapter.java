package com.example.cargotracker.tracking.interfaces.api.internal;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.InquiryStatisticsQueryService;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryCountSummary;
import com.example.cargotracker.tracking.interfaces.api.InquiryCounts;
import com.example.cargotracker.tracking.interfaces.api.InquiryStatistics;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 追跡の公開 API（期間の照会の件数）のインバウンドアダプター（US-21 AC4。W9 の Bolt 44-1）。照会のアプリケーションサービスに委ね、件数を公開 API の
 * 型に変える。
 */
@Component
public class InquiryStatisticsAdapter implements InquiryStatistics {

    private final InquiryStatisticsQueryService queryService;

    public InquiryStatisticsAdapter(InquiryStatisticsQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public InquiryCounts countBetween(Instant fromInclusive, Instant toExclusive) {
        InquiryCountSummary summary =
                queryService.countBetween(new UtcInstant(fromInclusive), new UtcInstant(toExclusive));
        return new InquiryCounts(summary.total(), summary.selfServed());
    }
}
