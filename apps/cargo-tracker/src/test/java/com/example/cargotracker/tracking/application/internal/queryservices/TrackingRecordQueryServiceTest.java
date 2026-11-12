package com.example.cargotracker.tracking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.rules.FreshnessPolicy;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 追跡記録の照会（S-11 追跡一覧、S-12 追跡の詳細。Bolt 26）。 */
class TrackingRecordQueryServiceTest {

    /** 追跡番号に使える文字（`TrackingNumber` の形式）。 */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final InMemoryTrackingRecordRepository repository = new InMemoryTrackingRecordRepository();
    private final CountingRepository counting = new CountingRepository(repository);
    /** 判定の時刻（鮮度の判定。Bolt 39g4）。既定は追跡の開始の前で、どの追跡も鮮度超過にならない。 */
    private final MutableClock clock = new MutableClock();

    private final TrackingRecordQueryService service = new TrackingRecordQueryService(
            counting, new TrackingRecordScope(counting), clock, new FreshnessPolicy(Duration.ofHours(36)));
    /** 追跡記録の荷主企業を担当する追跡管理者（Bolt 38g）。 */
    private final AuthenticatedActor manager =
            AuthenticatedActorFixture.trackingManagerHandling(new UserId(UUID.randomUUID()), TrackingFixture.SHIPPER);

    @Test
    void 追跡一覧は追跡の開始時刻の新しい順に要約を返す() {
        TrackingRecord older = started(1, "2026-10-26T01:00:00Z");
        TrackingRecord newer = started(2, "2026-10-26T02:00:00Z");

        RecentTrackingRecords recent = service.recent(manager);

        assertThat(recent.truncated()).isFalse();
        assertThat(recent.rows())
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(newer.trackingNumber(), older.trackingNumber());
        assertThat(recent.rows().getFirst())
                .isEqualTo(new TrackingRecordSummary(
                        newer.trackingNumber(),
                        TrackingStatus.PICKUP_SCHEDULED,
                        TrackingFixture.at("2026-11-15T00:00:00Z"),
                        TrackingFixture.at("2026-11-15T00:00:00Z"),
                        Optional.empty(),
                        Optional.of(TrackingFixture.at("2026-11-01T00:00:00Z")),
                        TrackingFixture.at("2026-10-26T02:00:00Z")));
    }

    /**
     * Bolt 39g4 の行 17（S-11 の鮮度超過の判定。FRS-02）。照会サービスが時計から判定の時刻を得て、行ごとに保存した次の予定の実績の予定時刻で
     * 判定する。次の予定の実績は区間 1 の出発予定 2026-11-01 00:00、許容範囲は 36 時間。
     */
    @Nested
    class Bolt39g4_追跡一覧の鮮度超過 {

        @Test
        void 行17a_予定時刻から37時間で次の予定の実績が届かなければ鮮度超過() {
            TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
            clock.setInstant(Instant.parse("2026-11-02T13:00:00Z"));

            RecentTrackingRecords recent = service.recent(manager);

            assertThat(recent.freshnessExceeded(recent.rows().getFirst())).isTrue();
            assertThat(recent.rows().getFirst().trackingNumber()).isEqualTo(saved.trackingNumber());
        }

        @Test
        void 行17b_予定時刻から1時間なら鮮度超過でない() {
            started(1, "2026-10-26T01:00:00Z");
            clock.setInstant(Instant.parse("2026-11-01T01:00:00Z"));

            RecentTrackingRecords recent = service.recent(manager);

            assertThat(recent.freshnessExceeded(recent.rows().getFirst())).isFalse();
        }

        @Test
        void 行17c_次の予定の実績がなければ鮮度超過でない() {
            TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
            repository.update(saved.registerMilestone(
                            MilestoneKind.ARRIVAL,
                            new Location("USLAX"),
                            TrackingFixture.at("2026-11-15T01:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", TrackingFixture.at("2026-11-15T02:00:00Z")),
                            new UserId(UUID.randomUUID()),
                            TrackingFixture.at("2026-11-15T02:00:00Z"))
                    .trackingRecord());
            clock.setInstant(Instant.parse("2026-12-31T00:00:00Z"));

            RecentTrackingRecords recent = service.recent(manager);

            assertThat(recent.rows().getFirst().nextExpectedAt()).isEmpty();
            assertThat(recent.freshnessExceeded(recent.rows().getFirst())).isFalse();
        }

        @Test
        void 行17d_確認中でも次の予定の実績が届かなければ鮮度超過() {
            TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
            TrackingRecord pickedUp = register(saved, MilestoneKind.PICKUP, "2026-11-01T10:00:00Z", "F-1");
            repository.update(pickedUp);
            TrackingRecord underReview = register(
                    repository.findByTrackingNumber(saved.trackingNumber()).orElseThrow(),
                    MilestoneKind.RECEIPT_AT_ORIGIN,
                    "2026-11-01T09:00:00Z",
                    "F-2");
            repository.update(underReview);
            clock.setInstant(Instant.parse("2026-11-02T13:00:00Z"));

            RecentTrackingRecords recent = service.recent(manager);

            assertThat(recent.rows().getFirst().currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(recent.freshnessExceeded(recent.rows().getFirst())).isTrue();
        }

        private static TrackingRecord register(
                TrackingRecord trackingRecord, MilestoneKind kind, String occurredAt, String reference) {
            return trackingRecord
                    .registerMilestone(
                            kind,
                            new Location("JPTYO"),
                            TrackingFixture.at(occurredAt),
                            new Source(SourceKind.FIELD_RECORD, reference, TrackingFixture.at("2026-11-02T00:00:00Z")),
                            new UserId(UUID.randomUUID()),
                            TrackingFixture.at("2026-11-02T00:00:00Z"))
                    .trackingRecord();
        }
    }

    /**
     * Bolt 39g4 の行 18（S-12 の鮮度超過の判定）。照会サービスが詳細と判定と許容範囲の組を返す（文の時間は許容範囲から作る）。許容範囲を 48 時間に
     * した照会サービスでは予定時刻 + 40 時間は鮮度超過でない（判定と文が設定の値に従う）。
     */
    @Nested
    class Bolt39g4_追跡の詳細の鮮度超過 {

        @Test
        void 行18_予定時刻から37時間で鮮度超過と許容範囲を返す() {
            TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
            clock.setInstant(Instant.parse("2026-11-02T13:00:00Z"));

            assertThat(service.detailWithFreshness(saved.trackingNumber(), manager))
                    .contains(new TrackingRecordDetail(saved, true, Duration.ofHours(36)));
        }

        @Test
        void 行18_予定時刻から1時間なら鮮度超過でない() {
            TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
            clock.setInstant(Instant.parse("2026-11-01T01:00:00Z"));

            assertThat(service.detailWithFreshness(saved.trackingNumber(), manager))
                    .contains(new TrackingRecordDetail(saved, false, Duration.ofHours(36)));
        }

        @Test
        void 行18_許容範囲を48時間にすると予定時刻から40時間は鮮度超過でない() {
            TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
            clock.setInstant(Instant.parse("2026-11-02T16:00:00Z"));
            TrackingRecordQueryService relaxed = new TrackingRecordQueryService(
                    counting, new TrackingRecordScope(counting), clock, new FreshnessPolicy(Duration.ofHours(48)));

            assertThat(relaxed.detailWithFreshness(saved.trackingNumber(), manager))
                    .contains(new TrackingRecordDetail(saved, false, Duration.ofHours(48)));
            assertThat(service.detailWithFreshness(saved.trackingNumber(), manager))
                    .contains(new TrackingRecordDetail(saved, true, Duration.ofHours(36)));
        }

        @Test
        void 行18_範囲の外とない追跡番号は空() {
            TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
            AuthenticatedActor outsider = AuthenticatedActorFixture.trackingManagerHandling(
                    new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()));

            assertThat(service.detailWithFreshness(saved.trackingNumber(), outsider))
                    .isEmpty();
            assertThat(service.detailWithFreshness(trackingNumber(2), manager)).isEmpty();
        }
    }

    /** 仮説 H1: 追跡記録が 3 件でも要約の照会は 1 回（行ごとに照会しない）。 */
    @Test
    void 追跡一覧は要約を1回の照会で引く() {
        for (int i = 1; i <= 3; i++) {
            started(i, "2026-10-26T0" + i + ":00:00Z");
        }

        service.recent(manager);

        assertThat(counting.summaryQueries).isEqualTo(1);
        assertThat(counting.detailQueries).isZero();
    }

    @Test
    void 追跡一覧は上限の50件までを返し超えたことを示す() {
        for (int i = 1; i <= 51; i++) {
            started(
                    i,
                    Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }

        RecentTrackingRecords recent = service.recent(manager);

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isTrue();
        assertThat(recent.limit()).isEqualTo(50);
        assertThat(recent.rows().getFirst().trackingNumber()).isEqualTo(trackingNumber(51));
        assertThat(recent.rows())
                .extracting(TrackingRecordSummary::trackingNumber)
                .doesNotContain(trackingNumber(1));
    }

    /** 上限ちょうどは超えていない（off-by-one の境界。Bolt 25b レビュー P-1 を最初から入れる）。 */
    @Test
    void 追跡一覧はちょうど50件なら上限を超えたと示さない() {
        for (int i = 1; i <= 50; i++) {
            started(
                    i,
                    Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }

        RecentTrackingRecords recent = service.recent(manager);

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isFalse();
    }

    @Test
    void 追跡記録がなければ空の一覧を返す() {
        RecentTrackingRecords recent = service.recent(manager);

        assertThat(recent.rows()).isEmpty();
        assertThat(recent.truncated()).isFalse();
    }

    @Test
    void 追跡一覧と詳細は担当企業の追跡記録だけを返し担当企業がなければ一覧を引かない() {
        // 行 1c・1d・2b・6（Bolt 38g）
        TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");
        AuthenticatedActor outsider = AuthenticatedActorFixture.trackingManagerHandling(
                new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()));
        AuthenticatedActor unassigned =
                AuthenticatedActorFixture.trackingManagerHandling(new UserId(UUID.randomUUID()));

        assertThat(service.recent(outsider).rows()).isEmpty();
        assertThat(service.detail(saved.trackingNumber(), outsider)).isEmpty();
        assertThat(service.outOfScope(saved.trackingNumber(), outsider)).isTrue();
        assertThat(service.outOfScope(saved.trackingNumber(), manager)).isFalse();
        assertThat(service.outOfScope(trackingNumber(2), outsider)).isFalse();
        int before = counting.summaryQueries;
        assertThat(service.recent(unassigned).rows()).isEmpty();
        assertThat(counting.summaryQueries).isEqualTo(before);
    }

    @Test
    void 追跡の詳細は追跡番号で追跡記録を返しない追跡番号は空() {
        TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");

        assertThat(service.detail(saved.trackingNumber(), manager)).containsSame(saved);
        assertThat(service.detail(trackingNumber(2), manager)).isEmpty();
    }

    private TrackingRecord started(int no, String startedAt) {
        TrackingRecord trackingRecord = TrackingRecord.start(
                        trackingNumber(no),
                        UUID.nameUUIDFromBytes(("booking-" + no).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        TrackingFixture.SHIPPER,
                        TrackingFixture.CONSIGNEE,
                        TrackingFixture.schedule(),
                        new UtcInstant(Instant.parse(startedAt)))
                .trackingRecord();
        repository.save(trackingRecord);
        return trackingRecord;
    }

    private static TrackingNumber trackingNumber(int no) {
        return new TrackingNumber(
                "CTAAAAAAAAAA" + ALPHABET.charAt(no / ALPHABET.length()) + ALPHABET.charAt(no % ALPHABET.length()));
    }

    /** 照会の回数を数える（仮説 H1）。 */
    private static final class CountingRepository implements TrackingRecordRepository {

        private final TrackingRecordRepository delegate;
        private int summaryQueries;
        private int detailQueries;

        CountingRepository(TrackingRecordRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(TrackingRecord trackingRecord) {
            delegate.save(trackingRecord);
        }

        @Override
        public void update(TrackingRecord trackingRecord) {
            delegate.update(trackingRecord);
        }

        @Override
        public Optional<TrackingRecord> findByBookingId(UUID bookingId) {
            detailQueries++;
            return delegate.findByBookingId(bookingId);
        }

        @Override
        public Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber) {
            detailQueries++;
            return delegate.findByTrackingNumber(trackingNumber);
        }

        @Override
        public Optional<TrackingRecord> findByTrackingNumberForExternalMilestone(TrackingNumber trackingNumber) {
            detailQueries++;
            return delegate.findByTrackingNumberForExternalMilestone(trackingNumber);
        }

        @Override
        public Optional<TrackingRecord> findByTrackingNumber(
                TrackingNumber trackingNumber, CompanyId shipperCompanyId) {
            detailQueries++;
            return delegate.findByTrackingNumber(trackingNumber, shipperCompanyId);
        }

        @Override
        public List<TrackingRecordSummary> findRecentSummariesByShipper(CompanyId shipperCompanyId, int limit) {
            summaryQueries++;
            return delegate.findRecentSummariesByShipper(shipperCompanyId, limit);
        }

        @Override
        public List<TrackingRecordSummary> findRecentSummaries(Set<CompanyId> shipperCompanyIds, int limit) {
            summaryQueries++;
            return delegate.findRecentSummaries(shipperCompanyIds, limit);
        }
    }
}
