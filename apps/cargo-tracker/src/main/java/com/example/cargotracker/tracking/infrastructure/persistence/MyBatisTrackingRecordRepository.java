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
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    /** 出典の一意制約（同じ出典の実績の同時の登録。T-INV-02。Bolt 39gb の P-6 で、この制約の違反だけを競合にする）。 */
    private static final String SOURCE_UNIQUE_KEY = "uk_milestone_source";

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
     * 主要実績を追加する（実績は削除・上書きしない。T-INV-01）。表で下書きの実績が集約で採用になっていれば、状態と採用者・採用時刻だけを書き換える（Bolt 39gb）。
     * 版が違えば、下書きの行がもう下書きでなければ、同じ出典の実績が同時に登録されて一意制約 {@code uk_milestone_source} に違反したら、または根拠の実績番号の実績が
     * 表になければ（遅延の外部キーの違反がコミットのときに 500 になるのを先に拾う。P-5）、セーブポイントに戻して {@link ConcurrentTrackingRecordUpdateException}
     * にする（PostgreSQL は制約違反でトランザクションを中断するため。貨物予約の保存と同じ）。ほかの一意制約の違反（実績番号の重なりなど）は版の条件の後には起きない
     * はずの誤りなので、例外のまま上げる（P-6。前例は貨物予約の保存の制約の名前で分ける形）。
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
            throw conflict(trackingRecord);
        }
        try {
            saveMilestones(trackingRecord);
        } catch (DuplicateKeyException e) {
            if (violates(e, SOURCE_UNIQUE_KEY)) {
                throw conflict(trackingRecord);
            }
            throw new IllegalStateException("主要実績の一意制約に違反した（実績番号の重なりなど）: " + trackingNumber, e);
        }
        if (statusBasisMilestoneNo != null && mapper.countMilestone(trackingNumber, statusBasisMilestoneNo) == 0) {
            throw conflict(trackingRecord);
        }
    }

    /** まだ表にない実績を追加し、表で下書きの実績が集約で採用になっていれば書き換える。表の状態が集約と違い、書き換えられなければ競合。 */
    private void saveMilestones(TrackingRecord trackingRecord) {
        String trackingNumber = trackingRecord.trackingNumber().value();
        Map<Integer, MilestoneRow> savedRows = mapper.findMilestones(trackingNumber).stream()
                .collect(Collectors.toMap(MilestoneRow::milestoneNo, row -> row));
        for (Milestone milestone : trackingRecord.milestones()) {
            MilestoneRow row = toRow(trackingNumber, milestone);
            MilestoneRow saved = savedRows.get(milestone.milestoneNo());
            if (saved == null) {
                mapper.insertMilestone(row);
            } else if (!sameState(saved, row) && (!isAdoption(milestone) || mapper.updateMilestoneAdoption(row) == 0)) {
                // 表の状態が集約と違う。下書きの採用でなければ、または表の行がもう下書きでなければ（別の採用が先に書いた）、競合
                throw conflict(trackingRecord);
            }
        }
    }

    /** 表の行と集約の実績の状態・採用者・採用時刻が同じか（同じなら書き換えない。採用時刻は瞬間で比べる）。 */
    private static boolean sameState(MilestoneRow saved, MilestoneRow row) {
        return saved.state().equals(row.state())
                && Objects.equals(saved.adoptedBy(), row.adoptedBy())
                && Objects.equals(
                        saved.adoptedAt() == null ? null : saved.adoptedAt().toInstant(),
                        row.adoptedAt() == null ? null : row.adoptedAt().toInstant());
    }

    /** 下書きの採用で書き換える実績か（外部原本の採用済み。採用者を持つ）。 */
    private static boolean isAdoption(Milestone milestone) {
        return milestone.state() == MilestoneState.ADOPTED && milestone.adoptedBy() != null;
    }

    private static ConcurrentTrackingRecordUpdateException conflict(TrackingRecord trackingRecord) {
        return new ConcurrentTrackingRecordUpdateException(
                trackingRecord.trackingNumber(), trackingRecord.aggregateVersion());
    }

    private static boolean violates(DuplicateKeyException e, String constraint) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraint);
    }

    private static MilestoneRow toRow(String trackingNumber, Milestone milestone) {
        ExternalOrigin origin = milestone.externalOrigin();
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
                offset(milestone.registeredAt()),
                origin == null ? null : origin.dataSourceId(),
                origin == null ? null : origin.externalId(),
                origin == null ? null : offset(origin.publishedAt()),
                milestone.adoptedBy() == null ? null : milestone.adoptedBy().value(),
                milestone.adoptedAt() == null ? null : offset(milestone.adoptedAt()));
    }

    private static Milestone toMilestone(MilestoneRow row) {
        ExternalOrigin origin = row.dataSourceId() == null
                ? null
                : new ExternalOrigin(row.dataSourceId(), row.externalId(), utc(row.publishedAt()));
        return new Milestone(
                row.milestoneNo(),
                MilestoneKind.valueOf(row.kind()),
                new Location(row.locationUnlocode()),
                utc(row.occurredAt()),
                new Source(SourceKind.valueOf(row.sourceKind()), row.sourceRef(), utc(row.acquiredAt())),
                MilestoneState.valueOf(row.state()),
                new UserId(row.registeredBy()),
                utc(row.registeredAt()),
                origin,
                row.adoptedBy() == null ? null : new UserId(row.adoptedBy()),
                row.adoptedAt() == null ? null : utc(row.adoptedAt()));
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

    /** 追跡番号の行を押さえてから組み立てる（押さえる前に読んだ値をキャッシュから使わない。Bolt 39g の決定 (4)）。 */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TrackingRecord> findByTrackingNumberForExternalMilestone(TrackingNumber trackingNumber) {
        if (mapper.lockByTrackingNumber(trackingNumber.value()) == null) {
            return Optional.empty();
        }
        return findByTrackingNumber(trackingNumber);
    }

    @Override
    public Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber, CompanyId shipperCompanyId) {
        return mapper.findTrackingRecordByTrackingNumberAndShipper(trackingNumber.value(), shipperCompanyId.value())
                .map(this::toRecord);
    }

    @Override
    public List<TrackingRecordSummary> findRecentSummariesByShipper(CompanyId shipperCompanyId, int limit) {
        return mapper.findRecentSummariesByShipper(shipperCompanyId.value(), limit).stream()
                .map(MyBatisTrackingRecordRepository::toSummary)
                .toList();
    }

    @Override
    public List<TrackingRecordSummary> findRecentSummaries(Set<CompanyId> shipperCompanyIds, int limit) {
        if (shipperCompanyIds.isEmpty()) {
            // IN () は書けない。判定の部品も空の集合では呼ばない（Bolt 38g。38f2 の前例）
            return List.of();
        }
        List<UUID> ids = shipperCompanyIds.stream().map(CompanyId::value).toList();
        return mapper.findRecentSummaries(ids, limit).stream()
                .map(MyBatisTrackingRecordRepository::toSummary)
                .toList();
    }

    private static TrackingRecordSummary toSummary(TrackingRecordSummaryRow row) {
        return new TrackingRecordSummary(
                new TrackingNumber(row.trackingNumber()),
                TrackingStatus.valueOf(row.currentStatus()),
                utc(row.originalEta()),
                utc(row.latestEta()),
                utc(row.createdAt()));
    }
}
