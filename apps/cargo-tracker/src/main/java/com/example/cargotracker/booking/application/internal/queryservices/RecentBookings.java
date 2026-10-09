package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

/**
 * 予約一覧（S-10 の最小の表示）の照会の結果（Bolt 25b）。確定時刻の新しい順の行と、上限を超えたか。画面の行の値だけを持ち、
 * 同じ形の型を重ねない（Bolt 23b の A-低4）。
 *
 * @param rows 確定時刻の新しい順の行（上限まで）
 * @param truncated 上限を超える予約があったか（画面に「新しい N 件だけを示しています」と示す）
 * @param limit 上限の件数（画面の文言に使う。上限の値を 1 か所に置く。Bolt 25b レビュー P-2）
 */
public record RecentBookings(List<Row> rows, boolean truncated, int limit) {

    public RecentBookings {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
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
