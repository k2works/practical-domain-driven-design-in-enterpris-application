package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    public boolean existsWebSelfAfter(UserId inquirerUserId, TrackingNumber trackingNumber, UtcInstant after) {
        return mapper.countWebSelfAfter(
                        inquirerUserId.value(),
                        trackingNumber.value(),
                        after.instant().atOffset(ZoneOffset.UTC))
                > 0;
    }
}
