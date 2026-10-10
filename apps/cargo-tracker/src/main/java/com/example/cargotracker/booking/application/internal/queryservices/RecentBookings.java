package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * 予約一覧（S-10 の最小の表示と C-06 の荷主の一覧）の照会の結果（Bolt 25b。C-06 は Bolt 27b）。確定時刻の新しい順の行と、上限を超えたか。画面の行の値だけを持ち、
 * 同じ形の型を重ねない（Bolt 23b の A-低4）。
 *
 * @param rows 確定時刻の新しい順の行（上限まで）
 * @param truncated 上限を超える予約があったか（画面に「新しい N 件だけを示しています」と示す）
 * @param limit 上限の件数（画面の文言に使う。上限の値を 1 か所に置く。Bolt 25b レビュー P-2）
 */
public record RecentBookings(List<Row> rows, boolean truncated, int limit) {

    /** 一覧の上限の件数（S-10 と C-06 で同じ。絞り込みとページ送りは W11）。 */
    public static final int LIMIT = 50;

    public RecentBookings {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }

    /**
     * 予約の要約を上限より 1 件多く引き、上限までの行と上限を超えたかを作る（S-10 と C-06 で同じ。上限と「1 件多く引く」の知識を
     * ここに集める。Bolt 27b）。予約サガの状態は、
     * 上限までの予約 ID で 1 回だけ引く（行ごとに照会しない）。予約があれば予約サガは同じトランザクションで作られている（ADR-015）ので、
     * ないのは不変条件の違反にする。
     *
     * @param summaries 件数を受け取って予約の要約を確定時刻の新しい順に引く照会
     * @param sagaStatuses 予約 ID の集合から予約サガの状態を引く照会
     * @return 照会の結果
     */
    public static RecentBookings of(
            IntFunction<List<BookingSummary>> summaries,
            Function<Set<BookingId>, Map<BookingId, BookingSagaStatus>> sagaStatuses) {
        List<BookingSummary> found = summaries.apply(LIMIT + 1);
        List<BookingSummary> shown = found.subList(0, Math.min(found.size(), LIMIT));
        Map<BookingId, BookingSagaStatus> statuses =
                sagaStatuses.apply(shown.stream().map(BookingSummary::bookingId).collect(Collectors.toSet()));
        List<Row> rows = shown.stream()
                .map(summary -> new Row(
                        summary.trackingNumber(),
                        summary.transportRequestNumber(),
                        summary.quotationNo(),
                        summary.committedAt(),
                        Optional.ofNullable(statuses.get(summary.bookingId()))
                                .orElseThrow(() -> missingSaga(summary.bookingId()))))
                .toList();
        return new RecentBookings(rows, found.size() > LIMIT, LIMIT);
    }

    /** 貨物予約があれば予約サガは同じトランザクションで作られている（ADR-015）。ないのは不変条件の違反。 */
    static IllegalStateException missingSaga(BookingId bookingId) {
        return new IllegalStateException("貨物予約に予約サガがない（本予約の確定と同じトランザクションで作る。ADR-015）: " + bookingId.value());
    }

    /**
     * 予約一覧の 1 行。
     *
     * @param trackingNumber 追跡番号
     * @param transportRequestNumber 業務番号の表記
     * @param quotationNo 見積り番号
     * @param committedAt 確定時刻（予約版 1 の commit 時刻。最初の確定の時刻）
     * @param sagaStatus 予約サガの状態（追跡の開始の表示に使う）
     */
    public record Row(
            TrackingNumber trackingNumber,
            String transportRequestNumber,
            int quotationNo,
            UtcInstant committedAt,
            BookingSagaStatus sagaStatus) {

        public Row {
            Objects.requireNonNull(trackingNumber, "trackingNumber");
            Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
            Objects.requireNonNull(committedAt, "committedAt");
            Objects.requireNonNull(sagaStatus, "sagaStatus");
        }
    }
}
