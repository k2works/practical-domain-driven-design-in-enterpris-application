package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 追跡記録のリポジトリの MyBatis 実装（ADR-015、T-INV-11。Bolt 25）。追跡記録と予定区間を同じトランザクションで追加する。予約 ID の
 * 一意制約（{@code uk_tracking_record_booking}）の違反は、そのまま例外にする（同時の DE-07 の負けた側。トランザクションを戻し、再配信で
 * 既存の追跡記録を見つけて何もしない）。作成時刻は追跡を開始した時刻。
 */
@Repository
public class MyBatisTrackingRecordRepository implements TrackingRecordRepository {

    private final TrackingRecordMapper mapper;

    public MyBatisTrackingRecordRepository(TrackingRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TrackingRecord trackingRecord) {
        String trackingNumber = trackingRecord.trackingNumber().value();
        OffsetDateTime startedAt = offset(trackingRecord.startedAt());
        Schedule schedule = trackingRecord.schedule();
        mapper.insertTrackingRecord(new TrackingRecordRow(
                trackingNumber,
                trackingRecord.bookingId(),
                trackingRecord.shipperCompanyId().value(),
                trackingRecord.consigneeCompanyId().value(),
                trackingRecord.bookingStatus().name(),
                schedule.routingCaseNumber(),
                schedule.routeVersionNo(),
                trackingRecord.currentStatus().name(),
                offset(trackingRecord.originalEta()),
                offset(trackingRecord.latestEta()),
                trackingRecord.aggregateVersion(),
                startedAt,
                startedAt));
        List<ScheduledLeg> legs = schedule.legs();
        for (int i = 0; i < legs.size(); i++) {
            ScheduledLeg leg = legs.get(i);
            mapper.insertScheduledLeg(new ScheduledLegRow(
                    trackingNumber,
                    i + 1,
                    leg.voyageNumber(),
                    leg.load().unLocode(),
                    leg.discharge().unLocode(),
                    offset(leg.departureAt()),
                    offset(leg.arrivalAt())));
        }
    }

    @Override
    public void update(TrackingRecord trackingRecord) {
        throw new UnsupportedOperationException("Bolt 26b のステップ 4 で作る");
    }

    @Override
    public Optional<TrackingRecord> findByBookingId(UUID bookingId) {
        return mapper.findTrackingRecordByBookingId(bookingId).map(this::toRecord);
    }

    private TrackingRecord toRecord(TrackingRecordRow row) {
        // 表では経路版と到着予定の列が NULL 可（実績の列と同じく後の Bolt の場面のため）だが、追跡記録は予定と到着予定を必ず持つ
        if (row.routingCaseNumber() == null
                || row.routeVersionNo() == null
                || row.originalEta() == null
                || row.latestEta() == null) {
            throw new IllegalStateException("追跡記録 " + row.trackingNumber() + " に予定の経路版または到着予定がありません（追跡の開始では必ず入れる）");
        }
        List<ScheduledLeg> legs = mapper.findScheduledLegs(row.trackingNumber()).stream()
                .map(leg -> new ScheduledLeg(
                        leg.voyageNumber(),
                        new Location(leg.loadUnlocode()),
                        new Location(leg.dischargeUnlocode()),
                        utc(leg.departureAt()),
                        utc(leg.arrivalAt())))
                .toList();
        return TrackingRecord.reconstitute(
                new TrackingNumber(row.trackingNumber()),
                row.bookingId(),
                new CompanyId(row.shipperCompanyId()),
                new CompanyId(row.consigneeCompanyId()),
                TrackedBookingStatus.valueOf(row.bookingStatus()),
                new Schedule(row.routingCaseNumber(), row.routeVersionNo(), legs),
                TrackingStatus.valueOf(row.currentStatus()),
                OptionalInt.empty(),
                List.of(),
                utc(row.originalEta()),
                utc(row.latestEta()),
                utc(row.createdAt()),
                row.version());
    }

    private static OffsetDateTime offset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }

    private static UtcInstant utc(OffsetDateTime value) {
        return new UtcInstant(value.toInstant());
    }

    @Override
    public Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber) {
        return mapper.findTrackingRecordByTrackingNumber(trackingNumber.value()).map(this::toRecord);
    }

    @Override
    public List<TrackingRecordSummary> findRecentSummaries(int limit) {
        return mapper.findRecentSummaries(limit).stream()
                .map(MyBatisTrackingRecordRepository::toSummary)
                .toList();
    }

    private static TrackingRecordSummary toSummary(TrackingRecordSummaryRow row) {
        // 集約の組み立てと同じく、表では NULL 可の当初の到着予定がない行は原因の分かる例外にする（Bolt 25 レビュー P-9）
        if (row.originalEta() == null) {
            throw new IllegalStateException("追跡記録 " + row.trackingNumber() + " に当初の到着予定がありません（追跡の開始では必ず入れる）");
        }
        return new TrackingRecordSummary(
                new TrackingNumber(row.trackingNumber()),
                TrackingStatus.valueOf(row.currentStatus()),
                utc(row.originalEta()),
                utc(row.createdAt()));
    }
}
