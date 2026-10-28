package com.example.cargotracker.booking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.RecordingBookableQuotationQuery;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 予約の照会（S-09・S-24。Bolt 23b）。S-09 を開いたときの判定は開いた時刻での参考で（ADR-016）、確定済みは予約の側で判定する
 * （見積りの公開 API では分からない）。S-24 は予約と予約サガの状態を返す。Bolt 38f2 から、どの照会も操作者の担当企業で絞り、
 * 範囲の判定を予約済み・予約サガの読み出しより先に置く（US-16 AC4）。
 */
class BookingQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");
    private static final UtcInstant APPROVED_AT = new UtcInstant(Instant.parse("2026-10-07T06:00:00Z"));
    private static final UtcInstant EXPIRES_AT = new UtcInstant(Instant.parse("2099-10-08T09:00:00Z"));
    private static final UserId SALES = new UserId(BookingFixture.SALES);
    private static final AuthenticatedActor HANDLER = AuthenticatedActorFixture.salesHandling(
            SALES, BookingFixture.terms().shipperCompanyId());
    private static final AuthenticatedActor OUTSIDER =
            AuthenticatedActorFixture.salesHandling(SALES, new CompanyId(UUID.randomUUID()));
    private static final AuthenticatedActor UNASSIGNED = AuthenticatedActorFixture.salesHandling(SALES);

    private final InMemoryBookingRepository bookings = new InMemoryBookingRepository();
    private final InMemoryBookingSagaRepository sagas = new InMemoryBookingSagaRepository();
    private final RecordingBookableQuotationQuery quotationQuery =
            new RecordingBookableQuotationQuery(request -> this.quotation);
    private BookableQuotationResult quotation = bookable();

    private final BookingQueryService service = new BookingQueryService(
            bookings, sagas, new QuotationBookability(quotationQuery), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void S09の本予約の確定の画面は見積りの行を押さえない今の照会を使う() {
        // Bolt 37e2（ADR-016 :34）。GET は読み取り専用のトランザクションなので行を押さえない
        service.confirmation("TR-2026-0001", 1, HANDLER);

        assertThat(quotationQuery.finds()).hasSize(1);
        assertThat(quotationQuery.confirmations()).isEmpty();
    }

    @Test
    void 確定に使える見積りは開いた時刻で照会して確定条件の表示に要る値を返す() {
        assertThat(service.confirmation("TR-2026-0001", 1, HANDLER))
                .isEqualTo(new BookingConfirmationPage.Available(BookingFixture.terms(), APPROVED_AT, EXPIRES_AT));
        assertThat(quotationQuery.all())
                .containsExactly(new BookableQuotationRequest("TR-2026-0001", 1, new UtcInstant(NOW), HANDLER));
    }

    @Test
    void 確定に使えない見積りは理由を返す() {
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirmation("TR-2026-0001", 1, HANDLER))
                .isEqualTo(new BookingConfirmationPage.Unavailable(QuotationUnavailability.EXPIRED));
    }

    @Test
    void 担当範囲の中で同じ見積りの予約があれば失効していても既存の追跡番号を返す() {
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirmation("TR-2026-0001", 1, HANDLER))
                .as("確定の後に見積りが失効しても、失効でなく同じ見積りの予約があると示す（Bolt 24、H1）")
                .isEqualTo(new BookingConfirmationPage.AlreadyBooked(BookingFixture.TRACKING_NUMBER));
        assertThat(quotationQuery.all()).as("範囲を確かめるため見積りの照会を先に流す（Bolt 38f2）").hasSize(1);
    }

    @Test
    void 担当範囲の外は予約済みでも追跡番号を示さず範囲の外を返す() {
        // 行 4b（V10）。範囲の判定を予約済みの判定より先に置く
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        quotation = new BookableQuotationResult.OutOfScope("TR-2026-0001", 1);

        assertThat(service.confirmation("TR-2026-0001", 1, OUTSIDER))
                .isEqualTo(new BookingConfirmationPage.OutOfScope());
    }

    @Test
    void 確定の前の範囲の確かめは見積りの行を押さえない照会で範囲の外なら正規の業務番号を返す() {
        // 行 3・4c。controller が確定のサービスの前に呼ぶ（ADR-019 の呼ぶ側の形）。記録の ID は生の入力ではなく正規の業務番号
        assertThat(service.outOfScopeNumber("TR-2026-0001", 1, HANDLER)).isEmpty();

        quotation = new BookableQuotationResult.OutOfScope("TR-2026-0001", 1);
        assertThat(service.outOfScopeNumber("tr-2026-0001", 1, OUTSIDER)).contains("TR-2026-0001");
        assertThat(quotationQuery.finds())
                .containsExactly(
                        new BookableQuotationRequest("TR-2026-0001", 1, new UtcInstant(NOW), HANDLER),
                        new BookableQuotationRequest("tr-2026-0001", 1, new UtcInstant(NOW), OUTSIDER));
        assertThat(quotationQuery.confirmations()).isEmpty();
    }

    @Test
    void 営業担当者でなければ範囲を確かめず確定のサービスの役割の判定に任せる() {
        // 順序は役割 → 範囲（記録しない役割の誤りを範囲の外として記録しない）
        quotation = new BookableQuotationResult.OutOfScope("TR-2026-0001", 1);
        AuthenticatedActor shipper =
                new AuthenticatedActor(SALES, new CompanyId(UUID.randomUUID()), Set.of(Role.SHIPPER), "荷主", "荷主 B");

        assertThat(service.outOfScopeNumber("TR-2026-0001", 1, shipper)).isEmpty();
        assertThat(quotationQuery.all()).isEmpty();
    }

    @Test
    void 予約の詳細は予約と予約サガの状態を返しない追跡番号は空() {
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagas.save(BookingSaga.start(booking.id(), booking.trackingNumber(), new UtcInstant(NOW)));

        assertThat(service.detail(booking.trackingNumber(), HANDLER))
                .contains(new BookingDetail(booking, BookingSagaStatus.IN_PROGRESS));
        assertThat(service.detail(new TrackingNumber("CTZZZZZZZZZZZZ"), HANDLER))
                .isEmpty();
    }

    @Test
    void 担当範囲の外の予約の詳細は予約サガを読まずに空を返す() {
        // 行 2a（V8）。範囲の判定は予約サガの読み出しより前（予約サガがなくても不変条件の違反にしない）
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));

        assertThat(service.detail(booking.trackingNumber(), OUTSIDER)).isEmpty();
        assertThat(service.detail(booking.trackingNumber(), UNASSIGNED)).isEmpty();
    }

    @Test
    void 予約一覧は担当企業の荷主の予約だけを返す() {
        // 行 1a・1b（V6）
        Booking inScope = booked(1, "2026-10-07T00:00:00Z");
        Booking outOfScope = booked(2, "2026-10-08T00:00:00Z", new CompanyId(UUID.randomUUID()));

        assertThat(service.recent(HANDLER).rows())
                .extracting(RecentBookings.Row::trackingNumber)
                .containsExactly(inScope.trackingNumber())
                .doesNotContain(outOfScope.trackingNumber());
    }

    @Test
    void 担当企業がなければ予約を読まずに空の一覧を返す() {
        // 行 7（V7）。IN () は書けない
        booked(1, "2026-10-07T00:00:00Z");
        AtomicInteger summaryQueries = new AtomicInteger();
        BookingQueryService counted = new BookingQueryService(
                new InMemoryBookingRepository() {
                    @Override
                    public List<BookingSummary> findRecentSummaries(Set<CompanyId> shipperCompanyIds, int limit) {
                        summaryQueries.incrementAndGet();
                        return List.of();
                    }
                },
                sagas,
                new QuotationBookability(quotationQuery),
                Clock.fixed(NOW, ZoneOffset.UTC));

        RecentBookings recent = counted.recent(UNASSIGNED);

        assertThat(recent.rows()).isEmpty();
        assertThat(recent.truncated()).isFalse();
        assertThat(summaryQueries).hasValue(0);
    }

    /** S-10 予約一覧（Bolt 25b）。確定時刻の新しい順に、予約サガの状態とともに返す。 */
    @Test
    void 予約一覧は確定時刻の新しい順に予約サガの状態とともに返す() {
        Booking older = booked(1, "2026-10-07T00:00:00Z");
        Booking newer = booked(2, "2026-10-08T00:00:00Z");
        sagas.update(sagas.findByBookingId(newer.id()).orElseThrow().complete());

        RecentBookings recent = service.recent(HANDLER);

        assertThat(recent.truncated()).isFalse();
        assertThat(recent.rows())
                .extracting(RecentBookings.Row::trackingNumber)
                .containsExactly(newer.trackingNumber(), older.trackingNumber());
        assertThat(recent.rows().getFirst())
                .isEqualTo(new RecentBookings.Row(
                        newer.trackingNumber(),
                        "TR-2026-0002",
                        1,
                        new UtcInstant(Instant.parse("2026-10-08T00:00:00Z")),
                        BookingSagaStatus.COMPLETED));
        assertThat(recent.rows().get(1).sagaStatus()).isEqualTo(BookingSagaStatus.IN_PROGRESS);
    }

    @Test
    void 予約一覧は上限の50件までを返し超えたことを示す() {
        for (int i = 1; i <= 51; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }

        RecentBookings recent = service.recent(HANDLER);

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isTrue();
        assertThat(recent.limit()).isEqualTo(50);
        assertThat(recent.rows().getFirst().transportRequestNumber()).isEqualTo("TR-2026-0051");
        assertThat(recent.rows())
                .extracting(RecentBookings.Row::transportRequestNumber)
                .doesNotContain("TR-2026-0001");
    }

    /** 上限ちょうどは超えていない（off-by-one の境界。Bolt 25b レビュー P-1）。 */
    @Test
    void 予約一覧はちょうど50件なら上限を超えたと示さない() {
        for (int i = 1; i <= 50; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }

        RecentBookings recent = service.recent(HANDLER);

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isFalse();
    }

    @Test
    void 予約がなければ空の一覧を返す() {
        RecentBookings recent = service.recent(HANDLER);

        assertThat(recent.rows()).isEmpty();
        assertThat(recent.truncated()).isFalse();
    }

    /** 予約の要約と予約サガの状態を、それぞれ 1 回の照会で引く（行ごとに照会しない。仮説 H1）。 */
    @Test
    void 予約一覧は予約の要約と予約サガの状態をそれぞれ1回の照会で引く() {
        for (int i = 1; i <= 3; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }
        AtomicInteger summaryQueries = new AtomicInteger();
        AtomicInteger statusQueries = new AtomicInteger();
        BookingQueryService counted = new BookingQueryService(
                new InMemoryBookingRepository() {
                    @Override
                    public List<BookingSummary> findRecentSummaries(Set<CompanyId> shipperCompanyIds, int limit) {
                        summaryQueries.incrementAndGet();
                        return bookings.findRecentSummaries(shipperCompanyIds, limit);
                    }
                },
                new InMemoryBookingSagaRepository() {
                    @Override
                    public Map<BookingId, BookingSagaStatus> findStatusesByBookingIds(Set<BookingId> bookingIds) {
                        statusQueries.incrementAndGet();
                        return sagas.findStatusesByBookingIds(bookingIds);
                    }
                },
                new QuotationBookability(new RecordingBookableQuotationQuery(request -> quotation)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(counted.recent(HANDLER).rows()).hasSize(3);
        assertThat(summaryQueries).hasValue(1);
        assertThat(statusQueries).hasValue(1);
    }

    @Test
    void 予約サガのない予約は不変条件の違反にする() {
        Booking booking = booked(1, "2026-10-07T00:00:00Z");
        sagas.clear();

        assertThatThrownBy(() -> service.recent(HANDLER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(booking.id().value().toString());
    }

    private Booking booked(int sequence, String committedAt) {
        return booked(sequence, committedAt, BookingFixture.terms().shipperCompanyId());
    }

    private Booking booked(int sequence, String committedAt, CompanyId shipper) {
        BookingTerms base = BookingFixture.terms();
        BookingTerms terms = new BookingTerms(
                UUID.randomUUID(),
                base.transportRequestVersionNo(),
                String.format("TR-2026-%04d", sequence),
                UUID.randomUUID(),
                1,
                shipper,
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
        UtcInstant at = new UtcInstant(Instant.parse(committedAt));
        Booking booking = Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        terms,
                        TrackingNumber.generate(new Random(sequence)),
                        BookingFixture.SALES,
                        at)
                .booking();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagas.save(BookingSaga.start(booking.id(), booking.trackingNumber(), at));
        return booking;
    }

    private static Booking confirmed() {
        return Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        BookingFixture.terms(),
                        BookingFixture.TRACKING_NUMBER,
                        BookingFixture.SALES,
                        new UtcInstant(NOW))
                .booking();
    }

    private static BookableQuotationResult bookable() {
        var terms = BookingFixture.terms();
        return new BookableQuotationResult.Bookable(
                terms.transportRequestId(),
                terms.transportRequestVersionNo(),
                terms.transportRequestNumber(),
                terms.quotationId(),
                1,
                terms.shipperCompanyId(),
                terms.consigneeCompanyId(),
                terms.routingCaseNumber(),
                terms.routeVersionNo(),
                terms.cargoCategory(),
                terms.cargoSummary(),
                terms.shipperApproverId(),
                APPROVED_AT,
                EXPIRES_AT);
    }
}
