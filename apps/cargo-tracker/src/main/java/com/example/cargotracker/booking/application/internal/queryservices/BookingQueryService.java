package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 予約の照会（S-09 本予約の確定、S-24 予約の詳細。Bolt 23b）。Bolt 38f2 から、どの照会も操作者の担当企業で絞る（US-16 AC4）。
 * 範囲の外は、いない予約・見積りと同じく画面が 404 にする。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code BookingConfiguration} が担う。
 */
@Service
public class BookingQueryService {

    private final BookingRepository repository;
    private final BookingSagaRepository sagaRepository;
    private final QuotationBookability quotations;
    private final Clock clock;

    public BookingQueryService(
            BookingRepository repository,
            BookingSagaRepository sagaRepository,
            QuotationBookability quotations,
            Clock clock) {
        this.repository = repository;
        this.sagaRepository = sagaRepository;
        this.quotations = quotations;
        this.clock = clock;
    }

    /**
     * 本予約の確定の画面を開いたときの判定。開いた時刻で見積りの公開 API に照会する参考の判定で、確定の可否は送ったときの照会
     * （commit 時刻）で決まる（ADR-016）。見積りの照会を先に流して担当範囲を確かめ、範囲の外なら予約済みでも追跡番号を示さない
     * （Bolt 38f2）。範囲の中では、同じ見積りの予約（見積りの公開 API では分からないので予約の側で業務番号と見積り番号から確かめる）を
     * 失効などの理由より先に示す（確定の後に見積りが失効しても失効と示さない。Bolt 24）。
     */
    @Transactional(readOnly = true)
    public BookingConfirmationPage confirmation(
            String transportRequestNumber, int quotationNo, AuthenticatedActor operator) {
        UtcInstant now = new UtcInstant(clock.instant());
        QuotationBookability.Result checked = quotations.check(transportRequestNumber, quotationNo, now, operator);
        // 範囲の外は予約済みより先に返す（追跡番号を示さない）。予約済みは使えない理由より先に返す（Bolt 24）
        return switch (checked) {
            case QuotationBookability.Result.OutOfScope _ -> new BookingConfirmationPage.OutOfScope();
            case QuotationBookability.Result.Unavailable(QuotationUnavailability reason) ->
                alreadyBookedOr(transportRequestNumber, quotationNo, new BookingConfirmationPage.Unavailable(reason));
            case QuotationBookability.Result.Bookable(
                    BookingTerms terms,
                    UtcInstant shipperApprovedAt,
                    UtcInstant expiresAt) ->
                alreadyBookedOr(
                        transportRequestNumber,
                        quotationNo,
                        new BookingConfirmationPage.Available(terms, shipperApprovedAt, expiresAt));
        };
    }

    private BookingConfirmationPage alreadyBookedOr(
            String transportRequestNumber, int quotationNo, BookingConfirmationPage otherwise) {
        Optional<TrackingNumber> booked = repository.findTrackingNumber(transportRequestNumber, quotationNo);
        return booked.<BookingConfirmationPage>map(BookingConfirmationPage.AlreadyBooked::new)
                .orElse(otherwise);
    }

    /**
     * 本予約の確定（POST）の前に、見積りの行を押さえない照会で担当範囲を確かめる（Bolt 38f2。ADR-019 の呼ぶ側の形）。controller が
     * 確定のサービスより先に呼び、範囲の外なら試行を記録して 404 にする。営業担当者でなければ範囲を確かめない（役割の判定は確定の
     * サービスが先に行い、役割の誤りを範囲の外として記録しない）。
     *
     * @return 営業担当者で、見積りの輸送要求が担当企業の外なら、見積りの公開 API が返す正規の業務番号（記録の ID。生の入力を ID にしない）。ほかは空
     */
    @Transactional(readOnly = true)
    public Optional<String> outOfScopeNumber(
            String transportRequestNumber, int quotationNo, AuthenticatedActor operator) {
        if (!operator.hasRole(Role.SALES)) {
            return Optional.empty();
        }
        return quotations.check(transportRequestNumber, quotationNo, new UtcInstant(clock.instant()), operator)
                        instanceof QuotationBookability.Result.OutOfScope outOfScope
                ? Optional.of(outOfScope.transportRequestNumber())
                : Optional.empty();
    }

    /**
     * 予約の詳細。予約と予約サガの状態を返す。予約があれば予約サガは同じトランザクションで作られている（ADR-015）。予約の荷主企業が
     * 操作者の担当企業でなければ、予約サガを読まずに空を返す（いない予約と同じ。Bolt 38f2）。
     */
    @Transactional(readOnly = true)
    public Optional<BookingDetail> detail(TrackingNumber trackingNumber, AuthenticatedActor operator) {
        return repository
                .findByTrackingNumber(trackingNumber)
                .filter(booking -> operator.handlesCompany(booking.shipperCompanyId()))
                .map(booking -> new BookingDetail(
                        booking,
                        sagaRepository
                                .findByBookingId(booking.id())
                                .orElseThrow(() -> RecentBookings.missingSaga(booking.id()))
                                .status()));
    }

    /**
     * 予約一覧（S-10 の最小の表示。Bolt 25b）。確定時刻の新しい順に上限まで返し、上限を超えたかを示す。予約の要約と予約サガの状態を
     * それぞれ 1 回の照会で引く（行ごとに照会しない）。予約があれば予約サガは同じトランザクションで作られている（ADR-015）。
     * 操作者の担当企業の荷主の予約だけを返し、担当企業がなければ読まずに空を返す（{@code IN ()} は書けない。Bolt 38f2）。
     */
    @Transactional(readOnly = true)
    public RecentBookings recent(AuthenticatedActor operator) {
        if (operator.hasNoAssignedCompanies()) {
            return RecentBookings.of(limit -> List.of(), sagaRepository::findStatusesByBookingIds);
        }
        return RecentBookings.of(
                limit -> repository.findRecentSummaries(operator.assignedCompanies(), limit),
                sagaRepository::findStatusesByBookingIds);
    }
}
