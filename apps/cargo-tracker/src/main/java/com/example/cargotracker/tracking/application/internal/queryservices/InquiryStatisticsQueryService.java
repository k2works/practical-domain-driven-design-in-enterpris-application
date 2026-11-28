package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryCountSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 期間の照会の件数を数える（KPI-02 の算式の材料。W9 の Bolt 44-1）。アクセス・監査が公開 API を通して週次に引く。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class InquiryStatisticsQueryService {

    private final InquiryRecordRepository inquiries;

    public InquiryStatisticsQueryService(InquiryRecordRepository inquiries) {
        this.inquiries = inquiries;
    }

    /** 受付時刻が範囲（始まりを含み、終わりを含まない）にある照会の件数。 */
    @Transactional(readOnly = true)
    public InquiryCountSummary countBetween(UtcInstant from, UtcInstant to) {
        return inquiries.countBetween(from, to);
    }
}
