package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryCountSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 照会記録の保存を覚えるだけのテスト用の代役（照会記録を使うサービスのテストが共有する。W9 の Bolt 44）。 */
public final class InMemoryInquiryRecordRepository implements InquiryRecordRepository {

    /** 保存した照会記録（保存の順）。 */
    public final List<InquiryRecord> records = new ArrayList<>();

    @Override
    public void save(InquiryRecord inquiryRecord) {
        records.add(inquiryRecord);
    }

    @Override
    public boolean existsWebSelfAfter(UserId user, TrackingNumber trackingNumber, UtcInstant after) {
        return records.stream()
                .anyMatch(r -> r.channel() == InquiryChannel.WEB_SELF
                        && r.inquirerUserId().filter(user::equals).isPresent()
                        && r.trackingNumber().filter(trackingNumber::equals).isPresent()
                        && r.receivedAt().instant().isAfter(after.instant()));
    }

    @Override
    public List<InquiryRecord> findUnescalatedWebSelf(
            CompanyId shipperCompanyId, TrackingNumber trackingNumber, UtcInstant from, UtcInstant to) {
        return records.stream()
                .filter(r -> r.channel() == InquiryChannel.WEB_SELF)
                .filter(r -> !r.escalated())
                .filter(r -> r.shipperCompanyId().equals(shipperCompanyId))
                .filter(r -> r.trackingNumber().filter(trackingNumber::equals).isPresent())
                .filter(r -> !r.receivedAt().instant().isBefore(from.instant())
                        && !r.receivedAt().instant().isAfter(to.instant()))
                .toList();
    }

    @Override
    public InquiryCountSummary countBetween(UtcInstant from, UtcInstant to) {
        List<InquiryRecord> inRange = records.stream()
                .filter(r -> !r.receivedAt().instant().isBefore(from.instant())
                        && r.receivedAt().instant().isBefore(to.instant()))
                .toList();
        long selfServed = inRange.stream()
                .filter(r -> r.channel() == InquiryChannel.WEB_SELF && !r.escalated())
                .count();
        return new InquiryCountSummary(inRange.size(), selfServed);
    }

    @Override
    public void escalate(UUID inquiryRecordId, CaseNumber caseNumber) {
        for (int i = 0; i < records.size(); i++) {
            InquiryRecord stored = records.get(i);
            if (stored.id().equals(inquiryRecordId) && !stored.escalated()) {
                records.set(i, stored.escalatedTo(caseNumber));
            }
        }
    }
}
