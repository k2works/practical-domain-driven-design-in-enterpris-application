package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryCountSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 照会記録のリポジトリの MyBatis 実装（Bolt 40-15）。呼び手のトランザクションの中で動く。
 */
@Repository
public class MyBatisInquiryRecordRepository implements InquiryRecordRepository {

    private final InquiryRecordMapper mapper;
    private final Clock clock;

    public MyBatisInquiryRecordRepository(InquiryRecordMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void save(InquiryRecord inquiryRecord) {
        mapper.insert(new InquiryRecordRow(
                inquiryRecord.id(),
                inquiryRecord.channel().name(),
                inquiryRecord.shipperCompanyId().value(),
                inquiryRecord.trackingNumber().map(TrackingNumber::value).orElse(null),
                inquiryRecord.inquirerKind().name(),
                inquiryRecord.inquirerUserId().map(UserId::value).orElse(null),
                inquiryRecord.receivedAt().instant().atOffset(ZoneOffset.UTC),
                inquiryRecord.escalated(),
                inquiryRecord.caseNumber().map(CaseNumber::value).orElse(null),
                inquiryRecord.recordedBy().map(UserId::value).orElse(null),
                OffsetDateTime.now(clock)));
    }

    @Override
    public List<InquiryRecord> findUnescalatedWebSelf(
            CompanyId shipperCompanyId, TrackingNumber trackingNumber, UtcInstant from, UtcInstant to) {
        return mapper
                .selectUnescalatedWebSelf(
                        shipperCompanyId.value(),
                        trackingNumber.value(),
                        from.instant().atOffset(ZoneOffset.UTC),
                        to.instant().atOffset(ZoneOffset.UTC))
                .stream()
                .map(MyBatisInquiryRecordRepository::toAggregate)
                .toList();
    }

    @Override
    public InquiryCountSummary countBetween(UtcInstant from, UtcInstant to) {
        OffsetDateTime start = from.instant().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.instant().atOffset(ZoneOffset.UTC);
        return new InquiryCountSummary(mapper.countBetween(start, end), mapper.countSelfServedBetween(start, end));
    }

    @Override
    public void escalate(UUID inquiryRecordId, CaseNumber caseNumber) {
        mapper.escalate(inquiryRecordId, caseNumber.value());
    }

    private static InquiryRecord toAggregate(InquiryRecordRow row) {
        return new InquiryRecord(
                row.id(),
                InquiryChannel.valueOf(row.channel()),
                new CompanyId(row.shipperCompanyId()),
                Optional.ofNullable(row.trackingNumber()).map(TrackingNumber::new),
                InquirerKind.valueOf(row.inquirerKind()),
                Optional.ofNullable(row.inquirerUserId()).map(UserId::new),
                new UtcInstant(row.receivedAt().toInstant()),
                Optional.ofNullable(row.caseNumber()).map(CaseNumber::new),
                Optional.ofNullable(row.recordedBy()).map(UserId::new));
    }

    @Override
    public boolean existsWebSelfAfter(UserId inquirerUserId, TrackingNumber trackingNumber, UtcInstant after) {
        return mapper.countWebSelfAfter(
                        inquirerUserId.value(),
                        trackingNumber.value(),
                        after.instant().atOffset(ZoneOffset.UTC))
                > 0;
    }
}
