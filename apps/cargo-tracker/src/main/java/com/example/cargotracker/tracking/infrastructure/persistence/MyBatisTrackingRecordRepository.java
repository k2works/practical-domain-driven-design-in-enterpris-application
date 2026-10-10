package com.example.cargotracker.tracking.infrastructure.persistence;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentTrackingRecordUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡記録のリポジトリの MyBatis 実装（ADR-015、T-INV-11。Bolt 25）。追跡記録と予定区間を同じトランザクションで追加する。予約 ID の
 * 一意制約（{@code uk_tracking_record_booking}）の違反は、そのまま例外にする（同時の DE-07 の負けた側。トランザクションを戻し、再配信で
 * 既存の追跡記録を見つけて何もしない）。作成時刻は追跡を開始した時刻。
 */
@Repository
public class MyBatisTrackingRecordRepository implements TrackingRecordRepository {

    private final TrackingRecordMapper mapper;
    private final Clock clock;

    public MyBatisTrackingRecordRepository(TrackingRecordMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
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
                null,
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

    /**
     * 主要実績の登録を保存する（Bolt 26b）。読み込んだときの版と同じときだけ現在状態と根拠の実績番号を書いて版を 1 増やし、まだ表にない
     * 主要実績を追加する（実績は削除・上書きしない。T-INV-01）。版が違えば、または同じ出典・同じ実績番号の実績が同時に登録されて一意制約に
     * 違反したら、セーブポイントに戻して {@link ConcurrentTrackingRecordUpdateException} にする（PostgreSQL は制約違反で
     * トランザクションを中断するため。貨物予約の保存と同じ）。
     */
    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void update(TrackingRecord trackingRecord) {
        String trackingNumber = trackingRecord.trackingNumber().value();
        Integer statusBasisMilestoneNo = trackingRecord.statusBasisMilestoneNo().isPresent()
                ? trackingRecord.statusBasisMilestoneNo().getAsInt()
                : null;
        if (mapper.updateTrackingRecord(
                        trackingNumber,
                        trackingRecord.aggregateVersion(),
                        trackingRecord.currentStatus().name(),
                        statusBasisMilestoneNo,
                        OffsetDateTime.now(clock))
                == 0) {
            throw new ConcurrentTrackingRecordUpdateException(
                    trackingRecord.trackingNumber(), trackingRecord.aggregateVersion());
        }
        Set<Integer> saved = mapper.findMilestones(trackingNumber).stream()
                .map(MilestoneRow::milestoneNo)
                .collect(Collectors.toSet());
        try {
            for (Milestone milestone : trackingRecord.milestones()) {
                if (!saved.contains(milestone.milestoneNo())) {
                    mapper.insertMilestone(toRow(trackingNumber, milestone));
                }
            }
        } catch (DuplicateKeyException _) {
            throw new ConcurrentTrackingRecordUpdateException(
                    trackingRecord.trackingNumber(), trackingRecord.aggregateVersion());
        }
    }

    private static MilestoneRow toRow(String trackingNumber, Milestone milestone) {
        return new MilestoneRow(
                trackingNumber,
                milestone.milestoneNo(),
                milestone.kind().name(),
                milestone.location().unLocode(),
                offset(milestone.occurredAt()),
                milestone.source().kind().name(),
                milestone.source().reference(),
                offset(milestone.source().acquiredAt()),
                milestone.state().name(),
                milestone.registeredBy().value(),
                offset(milestone.registeredAt()));
    }

    private static Milestone toMilestone(MilestoneRow row) {
        return new Milestone(
                row.milestoneNo(),
                MilestoneKind.valueOf(row.kind()),
                new Location(row.locationUnlocode()),
                utc(row.occurredAt()),
                new Source(SourceKind.valueOf(row.sourceKind()), row.sourceRef(), utc(row.acquiredAt())),
                MilestoneState.valueOf(row.state()),
                new UserId(row.registeredBy()),
                utc(row.registeredAt()));
    }

    @Override
    public Optional<TrackingRecord> findByBookingId(UUID bookingId) {
        return mapper.findTrackingRecordByBookingId(bookingId).map(this::toRecord);
    }

    private TrackingRecord toRecord(TrackingRecordRow row) {
        // 経路版と到着予定の 4 列は表で NOT NULL（Bolt 26c）
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
                row.statusBasisMilestoneNo() == null
                        ? OptionalInt.empty()
                        : OptionalInt.of(row.statusBasisMilestoneNo()),
                mapper.findMilestones(row.trackingNumber()).stream()
                        .map(MyBatisTrackingRecordRepository::toMilestone)
                        .toList(),
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
        return new TrackingRecordSummary(
                new TrackingNumber(row.trackingNumber()),
                TrackingStatus.valueOf(row.currentStatus()),
                utc(row.originalEta()),
                utc(row.createdAt()));
    }
}
