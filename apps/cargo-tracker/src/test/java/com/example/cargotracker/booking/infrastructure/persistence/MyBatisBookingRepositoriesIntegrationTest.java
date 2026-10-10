package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.application.sagas.ConcurrentBookingSagaUpdateException;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 貨物予約と予約サガのリポジトリ（PostgreSQL。Bolt 23）。処理済みコマンドと業務番号・見積り番号での照会（Bolt 24）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisBookingRepositoriesIntegrationTest {

    private static final UtcInstant COMMITTED_AT = new UtcInstant(Instant.parse("2026-10-08T08:59:00.123456Z"));

    @Autowired
    MyBatisBookingRepository repository;

    @Autowired
    MyBatisBookingSagaRepository sagaRepository;

    private final Random random = new Random();

    @Test
    void 確定した貨物予約を予約版とあわせて保存し追跡番号で読み出す() {
        Booking booking = confirmed(terms(UUID.randomUUID()));

        repository.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));

        Booking found =
                repository.findByTrackingNumber(booking.trackingNumber()).orElseThrow();
        assertThat(found.id()).isEqualTo(booking.id());
        assertThat(found.status()).isEqualTo(booking.status());
        assertThat(found.transportPhase()).isEqualTo(booking.transportPhase());
        assertThat(found.versions()).isEqualTo(booking.versions());
        assertThat(found.transportRequestNumber()).isEqualTo(booking.transportRequestNumber());
        assertThat(repository.existsByTrackingNumber(booking.trackingNumber())).isTrue();
        assertThat(repository.existsByTrackingNumber(TrackingNumber.generate(random)))
                .isFalse();
        assertThat(found.quotationNo()).isEqualTo(booking.quotationNo());
        assertThat(repository.findTrackingNumber(booking.transportRequestNumber(), booking.quotationNo()))
                .contains(booking.trackingNumber());
        assertThat(repository.findTrackingNumber(booking.transportRequestNumber(), booking.quotationNo() + 1))
                .isEmpty();
    }

    @Test
    void 処理済みコマンドを貨物予約と一緒に保存しコマンドIDで読み出す() {
        Booking booking = confirmed(terms(UUID.randomUUID()));
        ProcessedCommand processed = BookingFixture.processedCommand(booking);

        repository.save(booking, BookingFixture.SALES, processed);

        assertThat(repository.findProcessedCommand(processed.commandId())).contains(processed);
        assertThat(repository.findProcessedCommand(CommandId.random())).isEmpty();
    }

    @Test
    void 同じ見積りの二件目では処理済みコマンドも記録せずトランザクションは続けて使える() {
        UUID quotationId = UUID.randomUUID();
        Booking first = confirmed(terms(quotationId));
        repository.save(first, BookingFixture.SALES, BookingFixture.processedCommand(first));
        Booking second = confirmed(terms(quotationId, first.transportRequestNumber()));
        ProcessedCommand secondCommand = BookingFixture.processedCommand(second);

        assertThatThrownBy(() -> repository.save(second, BookingFixture.SALES, secondCommand))
                .isInstanceOf(DuplicateBookingException.class);

        assertThat(repository.findProcessedCommand(secondCommand.commandId()))
                .as("セーブポイントに戻り、同じトランザクションで読み直せる（Bolt 24、H2）")
                .isEmpty();
        assertThat(repository.findTrackingNumber(first.transportRequestNumber(), first.quotationNo()))
                .contains(first.trackingNumber());
    }

    @Test
    void 処理済みコマンドのコマンドIDの重なりは同時の確定の決着として扱い別の見積りの予約も記録しない() {
        Booking first = confirmed(terms(UUID.randomUUID()));
        ProcessedCommand processed = BookingFixture.processedCommand(first);
        repository.save(first, BookingFixture.SALES, processed);

        Booking other = confirmed(terms(UUID.randomUUID()));
        ProcessedCommand sameCommandId = ProcessedCommand.confirmBooking(
                processed.commandId(),
                other.transportRequestNumber(),
                other.quotationNo(),
                new UserId(BookingFixture.SALES),
                other.id(),
                other.trackingNumber(),
                COMMITTED_AT);
        assertThatThrownBy(() -> repository.save(other, BookingFixture.SALES, sameCommandId))
                .as("同じコマンド ID で別の見積りを送った衝突。呼び出し側が読み直して衝突を返す（Bolt 24 レビュー P-1）")
                .isInstanceOf(DuplicateBookingException.class);
        assertThat(repository.findTrackingNumber(other.transportRequestNumber(), other.quotationNo()))
                .as("貨物予約の保存もセーブポイントで戻る")
                .isEmpty();
    }

    @Test
    void 同じ見積りの二件目の予約はドメインの例外になる() {
        UUID quotationId = UUID.randomUUID();
        Booking first = confirmed(terms(quotationId));
        repository.save(first, BookingFixture.SALES, BookingFixture.processedCommand(first));

        Booking second = confirmed(terms(quotationId));
        ProcessedCommand secondCommand = BookingFixture.processedCommand(second);
        assertThatThrownBy(() -> repository.save(second, BookingFixture.SALES, secondCommand))
                .isInstanceOf(DuplicateBookingException.class);
        assertThat(repository.findByTrackingNumber(second.trackingNumber())).isEmpty();
    }

    @Test
    void 追跡番号の重なりは同じ見積りの予約と区別して技術の失敗にする() {
        Booking first = confirmed(terms(UUID.randomUUID()));
        repository.save(first, BookingFixture.SALES, BookingFixture.processedCommand(first));

        Booking collided = confirmed(terms(UUID.randomUUID()), first.trackingNumber());
        ProcessedCommand collidedCommand = BookingFixture.processedCommand(collided);
        assertThatThrownBy(() -> repository.save(collided, BookingFixture.SALES, collidedCommand))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DuplicateBookingException.class);
    }

    @Test
    void 予約サガを処理中で保存し予約IDで読み出す() {
        Booking booking = confirmed(terms(UUID.randomUUID()));
        repository.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        BookingSaga saga = BookingSaga.start(booking.id(), booking.trackingNumber(), COMMITTED_AT);

        sagaRepository.save(saga);

        BookingSaga found = sagaRepository.findByBookingId(booking.id()).orElseThrow();
        assertThat(found.id()).isEqualTo(saga.id());
        assertThat(found.status()).isEqualTo(BookingSagaStatus.IN_PROGRESS);
        assertThat(found.trackingNumber()).isEqualTo(booking.trackingNumber());
        assertThat(found.startedAt()).isEqualTo(COMMITTED_AT);
    }

    /** 予約サガの完了は期待版で更新し、版を 1 進める（ARCH-HO-01。Bolt 25）。 */
    @Test
    void 予約サガを期待版で完了にし版を進める() {
        Booking booking = confirmed(terms(UUID.randomUUID()));
        repository.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagaRepository.save(BookingSaga.start(booking.id(), booking.trackingNumber(), COMMITTED_AT));
        BookingSaga started = sagaRepository.findByBookingId(booking.id()).orElseThrow();

        sagaRepository.update(started.complete());

        BookingSaga found = sagaRepository.findByBookingId(booking.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(BookingSagaStatus.COMPLETED);
        assertThat(found.version()).isEqualTo(started.version() + 1);
        assertThat(found.startedAt()).isEqualTo(COMMITTED_AT);
    }

    @Test
    void 読んだ後にほかの更新が先に保存された予約サガは更新しない() {
        Booking booking = confirmed(terms(UUID.randomUUID()));
        repository.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagaRepository.save(BookingSaga.start(booking.id(), booking.trackingNumber(), COMMITTED_AT));
        BookingSaga started = sagaRepository.findByBookingId(booking.id()).orElseThrow();
        sagaRepository.update(started.complete());
        BookingSaga stale = started.complete();

        assertThatThrownBy(() -> sagaRepository.update(stale)).isInstanceOf(ConcurrentBookingSagaUpdateException.class);
    }

    private Booking confirmed(BookingTerms terms) {
        return confirmed(terms, TrackingNumber.generate(random));
    }

    private static Booking confirmed(BookingTerms terms, TrackingNumber trackingNumber) {
        return Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        terms,
                        trackingNumber,
                        BookingFixture.SALES,
                        COMMITTED_AT)
                .booking();
    }

    /**
     * S-10 予約一覧（Bolt 25b）。予約の要約を確定時刻（予約版 1）の新しい順、同じ時刻なら追跡番号の順に上限まで返す。ほかのテストが
     * コミットした予約もあるので、このテストの予約は遠い先の確定時刻にして一覧の先頭に来るようにする。
     */
    @Test
    void 予約の要約を確定時刻の新しい順に上限まで返す() {
        Booking oldest = savedAt("2099-12-01T00:00:00Z", new TrackingNumber("CTZZZZZZZZZZZ2"));
        Booking sameTimeLater = savedAt("2099-12-02T00:00:00Z", new TrackingNumber("CTZZZZZZZZZZZ4"));
        Booking sameTimeFirst = savedAt("2099-12-02T00:00:00Z", new TrackingNumber("CTZZZZZZZZZZZ3"));

        assertThat(repository.findRecentSummaries(3))
                .containsExactly(
                        summary(sameTimeFirst, "2099-12-02T00:00:00Z"),
                        summary(sameTimeLater, "2099-12-02T00:00:00Z"),
                        summary(oldest, "2099-12-01T00:00:00Z"));
        assertThat(repository.findRecentSummaries(1)).containsExactly(summary(sameTimeFirst, "2099-12-02T00:00:00Z"));
    }

    /** C-06 予約一覧（荷主。BR-07。Bolt 27b）。荷主企業の予約だけを、S-10 と同じ並びで上限まで返す。他社の予約は出さない。 */
    @Test
    void 荷主企業の予約の要約だけを確定時刻の新しい順に上限まで返す() {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        Booking older = savedAt("2099-10-01T00:00:00Z", TrackingNumber.generate(random), shipper);
        Booking sameTimeLater = savedAt("2099-10-02T00:00:00Z", new TrackingNumber("CTYYYYYYYYYYY4"), shipper);
        Booking sameTimeFirst = savedAt("2099-10-02T00:00:00Z", new TrackingNumber("CTYYYYYYYYYYY3"), shipper);
        savedAt("2099-10-03T00:00:00Z", TrackingNumber.generate(random), new CompanyId(UUID.randomUUID()));

        assertThat(repository.findRecentSummariesByShipper(shipper, 10))
                .containsExactly(
                        summary(sameTimeFirst, "2099-10-02T00:00:00Z"),
                        summary(sameTimeLater, "2099-10-02T00:00:00Z"),
                        summary(older, "2099-10-01T00:00:00Z"));
        assertThat(repository.findRecentSummariesByShipper(shipper, 1))
                .containsExactly(summary(sameTimeFirst, "2099-10-02T00:00:00Z"));
        assertThat(repository.findRecentSummariesByShipper(new CompanyId(UUID.randomUUID()), 10))
                .isEmpty();
    }

    @Test
    void 予約サガの状態を予約IDの集合でまとめて返す() {
        Booking inProgress = savedAt("2099-11-01T00:00:00Z", TrackingNumber.generate(random));
        Booking completed = savedAt("2099-11-02T00:00:00Z", TrackingNumber.generate(random));
        sagaRepository.update(
                sagaRepository.findByBookingId(completed.id()).orElseThrow().complete());
        BookingId unknown = new BookingId(UUID.randomUUID());

        assertThat(sagaRepository.findStatusesByBookingIds(Set.of(inProgress.id(), completed.id(), unknown)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        inProgress.id(), BookingSagaStatus.IN_PROGRESS, completed.id(), BookingSagaStatus.COMPLETED));
        assertThat(sagaRepository.findStatusesByBookingIds(Set.of())).isEmpty();
    }

    private Booking savedAt(String committedAt, TrackingNumber trackingNumber) {
        return savedAt(committedAt, trackingNumber, BookingFixture.terms().shipperCompanyId());
    }

    private Booking savedAt(String committedAt, TrackingNumber trackingNumber, CompanyId shipper) {
        UtcInstant at = new UtcInstant(Instant.parse(committedAt));
        Booking booking = Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        terms(UUID.randomUUID(), shipper),
                        trackingNumber,
                        BookingFixture.SALES,
                        at)
                .booking();
        repository.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagaRepository.save(BookingSaga.start(booking.id(), booking.trackingNumber(), at));
        return booking;
    }

    private static BookingSummary summary(Booking booking, String committedAt) {
        return new BookingSummary(
                booking.id(),
                booking.trackingNumber(),
                booking.transportRequestNumber(),
                booking.quotationNo(),
                new UtcInstant(Instant.parse(committedAt)));
    }

    /** 見積り ID ごとに新しい業務番号（業務番号と見積り番号の一意制約に当たらないように。2082 年はほかのテストと重ならない）。 */
    private BookingTerms terms(UUID quotationId) {
        return terms(quotationId, BookingFixture.terms().shipperCompanyId());
    }

    private BookingTerms terms(UUID quotationId, CompanyId shipper) {
        return terms(quotationId, "TR-2082-" + (100000 + random.nextInt(900000)), shipper);
    }

    private static BookingTerms terms(UUID quotationId, String transportRequestNumber) {
        return terms(quotationId, transportRequestNumber, BookingFixture.terms().shipperCompanyId());
    }

    private static BookingTerms terms(UUID quotationId, String transportRequestNumber, CompanyId shipper) {
        BookingTerms base = BookingFixture.terms();
        return new BookingTerms(
                base.transportRequestId(),
                base.transportRequestVersionNo(),
                transportRequestNumber,
                quotationId,
                base.quotationNo(),
                shipper,
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
    }
}
