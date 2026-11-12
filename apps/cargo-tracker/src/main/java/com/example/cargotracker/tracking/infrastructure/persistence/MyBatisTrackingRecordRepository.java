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
import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.UnderReviewReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyageArrival;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
        TrackingRecordMapper.VoyageArrivalColumns voyageArrivalColumns = voyageArrivalColumns(trackingRecord);
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
                null,
                offset(trackingRecord.originalEta()),
                offset(trackingRecord.latestEta()),
                trackingRecord
                        .lastAcquiredAt()
                        .map(MyBatisTrackingRecordRepository::offset)
                        .orElse(null),
                offsetOrNull(trackingRecord.nextExpectedAt()),
                voyageArrivalColumns.arrivalAt(),
                voyageArrivalColumns.publishedAt(),
                voyageArrivalColumns.acquiredAt(),
                voyageArrivalColumns.sourceRef(),
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
     * 主要実績の登録を保存する（Bolt 26b）。読み込んだときの版と同じときだけ現在状態と根拠の実績番号と確認中の理由（Bolt 39gc）と最新の到着見込みと最終取得時刻
     * （Bolt 39g3。当初の到着予定と経路版は書かない）と次の予定の実績の予定時刻（Bolt 39g4）と航海の到着予定と順の鍵の 4 列（Bolt 39g4b）を書いて版を 1 増やし、表にない
     * 実績番号の主要実績を追加する（実績は削除・上書きしない。T-INV-01）。表にある実績は中身が集約と同じでなければ、版を通さずに書いた経路の誤りとして
     * {@link IllegalStateException} にする（P-3。Bolt 39g2）。表で下書きの実績が集約で採用・確認中になっていれば、状態と採用者・採用時刻だけを書き換える（Bolt 39gb。確認中は Bolt 39gc）。
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
                        trackingRecord.underReviewReason().map(Enum::name).orElse(null),
                        offset(trackingRecord.latestEta()),
                        trackingRecord
                                .lastAcquiredAt()
                                .map(MyBatisTrackingRecordRepository::offset)
                                .orElse(null),
                        offsetOrNull(trackingRecord.nextExpectedAt()),
                        voyageArrivalColumns(trackingRecord),
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

    /**
     * 表にない実績番号の実績を追加し、表で下書きの実績が集約で採用になっていれば書き換える。表の状態が集約と違い、書き換えられなければ競合。
     * 表にある実績は集約の実績と中身（種類・場所・発生時刻・出典・登録者・登録時刻・外部の出どころ）が同じでなければならず、表にあって集約にない実績も
     * あってはならない（P-3。Bolt 39g2）。版の条件を通った後にそれが崩れているのは、版を通さずに実績を書いた経路があるという誤りなので、黙って飛ばさずに
     * {@link IllegalStateException} でトランザクションを戻す（P-6 と同じく例外のまま上げる）。比べには読み込んでいる表の行を使い、照会は増やさない。
     */
    private void saveMilestones(TrackingRecord trackingRecord) {
        String trackingNumber = trackingRecord.trackingNumber().value();
        Map<Integer, MilestoneRow> savedRows = mapper.findMilestones(trackingNumber).stream()
                .collect(Collectors.toMap(MilestoneRow::milestoneNo, row -> row));
        for (Milestone milestone : trackingRecord.milestones()) {
            MilestoneRow row = toRow(trackingNumber, milestone);
            MilestoneRow saved = savedRows.get(milestone.milestoneNo());
            if (saved == null) {
                mapper.insertMilestone(row);
                continue;
            }
            if (!sameContent(saved, row)) {
                throw new IllegalStateException(
                        "表の主要実績の中身が追跡記録と違う（版を通さずに書いた経路の誤り）: " + trackingNumber + " の実績 " + milestone.milestoneNo());
            }
            if (!sameState(saved, row) && (!isAdoption(milestone) || mapper.updateMilestoneAdoption(row) == 0)) {
                // 表の状態が集約と違う。下書きの採用でなければ、または表の行がもう下書きでなければ（別の採用が先に書いた）、競合
                throw conflict(trackingRecord);
            }
        }
        // 集約の実績番号は 1 からの連番なので、実績の数より大きい実績番号の行は集約にない。追加の後に見るので、同じ出典の同時の登録は先に
        // 一意制約の違反（競合）になる
        if (savedRows.keySet().stream()
                .anyMatch(
                        milestoneNo -> milestoneNo > trackingRecord.milestones().size())) {
            throw new IllegalStateException("表にあって追跡記録にない主要実績がある（版を通さずに書いた経路の誤り）: " + trackingNumber);
        }
    }

    /** 表の行と集約の実績の中身が同じか（状態・採用者・採用時刻は {@link #sameState} が比べる。時刻は表の精度のマイクロ秒で比べる。P-3）。 */
    private static boolean sameContent(MilestoneRow saved, MilestoneRow row) {
        return saved.kind().equals(row.kind())
                && saved.locationUnlocode().equals(row.locationUnlocode())
                && sameInstant(saved.occurredAt(), row.occurredAt())
                && sameSourceAndRegistration(saved, row)
                && sameExternalOrigin(saved, row);
    }

    /** 出典（種類・参照・取得時刻）と登録者・登録時刻が同じか（P-3）。 */
    private static boolean sameSourceAndRegistration(MilestoneRow saved, MilestoneRow row) {
        return saved.sourceKind().equals(row.sourceKind())
                && saved.sourceRef().equals(row.sourceRef())
                && sameInstant(saved.acquiredAt(), row.acquiredAt())
                && saved.registeredBy().equals(row.registeredBy())
                && sameInstant(saved.registeredAt(), row.registeredAt());
    }

    /** 外部の出どころ（情報源 ID・外部 ID・公開した時刻）が同じか（P-3）。 */
    private static boolean sameExternalOrigin(MilestoneRow saved, MilestoneRow row) {
        return Objects.equals(saved.dataSourceId(), row.dataSourceId())
                && Objects.equals(saved.externalId(), row.externalId())
                && sameInstant(saved.publishedAt(), row.publishedAt());
    }

    private static boolean sameInstant(OffsetDateTime saved, OffsetDateTime row) {
        return Objects.equals(
                saved == null ? null : saved.toInstant().truncatedTo(ChronoUnit.MICROS),
                row == null ? null : row.toInstant().truncatedTo(ChronoUnit.MICROS));
    }

    /** 表の行と集約の実績の状態・採用者・採用時刻が同じか（同じなら書き換えない。採用時刻は瞬間で比べる）。 */
    private static boolean sameState(MilestoneRow saved, MilestoneRow row) {
        return saved.state().equals(row.state())
                && Objects.equals(saved.adoptedBy(), row.adoptedBy())
                && Objects.equals(
                        saved.adoptedAt() == null ? null : saved.adoptedAt().toInstant(),
                        row.adoptedAt() == null ? null : row.adoptedAt().toInstant());
    }

    /** 下書きの採用で書き換える実績か（外部原本の採用済み・確認中。採用者を持つ。確認中は Bolt 39gc）。 */
    private static boolean isAdoption(Milestone milestone) {
        return (milestone.state() == MilestoneState.ADOPTED || milestone.state() == MilestoneState.UNDER_REVIEW)
                && milestone.adoptedBy() != null;
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
                Optional.ofNullable(row.underReviewReason()).map(UnderReviewReason::valueOf),
                mapper.findMilestones(row.trackingNumber()).stream()
                        .map(MyBatisTrackingRecordRepository::toMilestone)
                        .toList(),
                utc(row.originalEta()),
                utc(row.latestEta()),
                Optional.ofNullable(row.lastAcquiredAt()).map(MyBatisTrackingRecordRepository::utc),
                Optional.ofNullable(row.nextExpectedAt()).map(MyBatisTrackingRecordRepository::utc),
                voyageArrivalOf(row),
                utc(row.createdAt()),
                row.version());
    }

    /** 航海の到着予定と順の鍵の 4 列の値（受けていなければ 4 つとも null。Bolt 39g4b）。 */
    private static TrackingRecordMapper.VoyageArrivalColumns voyageArrivalColumns(TrackingRecord trackingRecord) {
        return trackingRecord
                .voyageArrival()
                .map(arrival -> new TrackingRecordMapper.VoyageArrivalColumns(
                        offsetOrNull(arrival.arrivalAt()),
                        offset(arrival.order().publishedAt()),
                        offset(arrival.order().acquiredAt()),
                        arrival.order().sourceReference()))
                .orElseGet(() -> new TrackingRecordMapper.VoyageArrivalColumns(null, null, null, null));
    }

    /** 4 列から航海の到着予定を組み立てる（順の鍵が NULL なら受けていない。順の鍵がそろうことは CHECK ck_tracking_record_voyage_arrival が守る）。 */
    private static Optional<VoyageArrival> voyageArrivalOf(TrackingRecordRow row) {
        if (row.voyageArrivalPublishedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(new VoyageArrival(
                Optional.ofNullable(row.voyageArrivalAt()).map(MyBatisTrackingRecordRepository::utc),
                new PublicationOrder(
                        utc(row.voyageArrivalPublishedAt()),
                        utc(row.voyageArrivalAcquiredAt()),
                        row.voyageArrivalSourceRef())));
    }

    @Override
    public List<TrackingNumber> findTrackingNumbersByFinalLegVoyage(String voyageNumber) {
        return mapper.findTrackingNumbersByFinalLegVoyage(voyageNumber).stream()
                .map(TrackingNumber::new)
                .toList();
    }

    private static OffsetDateTime offset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }

    /** 空なら NULL（NULL 可の日時の列。Bolt 39g4）。 */
    private static OffsetDateTime offsetOrNull(Optional<UtcInstant> instant) {
        return instant.map(MyBatisTrackingRecordRepository::offset).orElse(null);
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
    public Optional<TrackingRecord> findByTrackingNumberForUpdate(TrackingNumber trackingNumber) {
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
                Optional.ofNullable(row.lastAcquiredAt()).map(MyBatisTrackingRecordRepository::utc),
                Optional.ofNullable(row.nextExpectedAt()).map(MyBatisTrackingRecordRepository::utc),
                utc(row.createdAt()));
    }
}
