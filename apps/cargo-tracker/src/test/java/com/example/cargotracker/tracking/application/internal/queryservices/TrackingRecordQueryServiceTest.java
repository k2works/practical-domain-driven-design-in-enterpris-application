package com.example.cargotracker.tracking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 追跡記録の照会（S-11 追跡一覧、S-12 追跡の詳細。Bolt 26）。 */
class TrackingRecordQueryServiceTest {

    /** 追跡番号に使える文字（`TrackingNumber` の形式）。 */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final InMemoryTrackingRecordRepository repository = new InMemoryTrackingRecordRepository();
    private final CountingRepository counting = new CountingRepository(repository);
    private final TrackingRecordQueryService service = new TrackingRecordQueryService(counting);

    @Test
    void 追跡一覧は追跡の開始時刻の新しい順に要約を返す() {
        TrackingRecord older = started(1, "2026-10-26T01:00:00Z");
        TrackingRecord newer = started(2, "2026-10-26T02:00:00Z");

        RecentTrackingRecords recent = service.recent();

        assertThat(recent.truncated()).isFalse();
        assertThat(recent.rows())
                .extracting(TrackingRecordSummary::trackingNumber)
                .containsExactly(newer.trackingNumber(), older.trackingNumber());
        assertThat(recent.rows().getFirst())
                .isEqualTo(new TrackingRecordSummary(
                        newer.trackingNumber(),
                        TrackingStatus.PICKUP_SCHEDULED,
                        TrackingFixture.at("2026-11-15T00:00:00Z"),
                        TrackingFixture.at("2026-10-26T02:00:00Z")));
    }

    /** 仮説 H1: 追跡記録が 3 件でも要約の照会は 1 回（行ごとに照会しない）。 */
    @Test
    void 追跡一覧は要約を1回の照会で引く() {
        for (int i = 1; i <= 3; i++) {
            started(i, "2026-10-26T0" + i + ":00:00Z");
        }

        service.recent();

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

        RecentTrackingRecords recent = service.recent();

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

        RecentTrackingRecords recent = service.recent();

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isFalse();
    }

    @Test
    void 追跡記録がなければ空の一覧を返す() {
        RecentTrackingRecords recent = service.recent();

        assertThat(recent.rows()).isEmpty();
        assertThat(recent.truncated()).isFalse();
    }

    @Test
    void 追跡の詳細は追跡番号で追跡記録を返しない追跡番号は空() {
        TrackingRecord saved = started(1, "2026-10-26T01:00:00Z");

        assertThat(service.detail(saved.trackingNumber())).containsSame(saved);
        assertThat(service.detail(trackingNumber(2))).isEmpty();
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
        public List<TrackingRecordSummary> findRecentSummaries(int limit) {
            summaryQueries++;
            return delegate.findRecentSummaries(limit);
        }
    }
}
