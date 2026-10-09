package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 予約の要約（S-10 予約一覧の読み取りモデル。Bolt 25b）。集約を組み立てずに、一覧に要る値だけを予約の表から引く。前例
 * （{@code RoutingCaseSummary} など）と同じく、ドメイン層の一覧の読み取りモデルで、自分の集約の表だけを結ぶ。予約サガの状態は
 * 持たない（照会のサービスが予約サガのリポジトリからまとめて引く）。
 *
 * @param bookingId 予約 ID
 * @param trackingNumber 追跡番号
 * @param transportRequestNumber 業務番号の表記
 * @param quotationNo 見積り番号
 * @param committedAt 確定時刻（予約版 1 の commit 時刻。最初の確定の時刻）
 */
public record BookingSummary(
        BookingId bookingId,
        TrackingNumber trackingNumber,
        String transportRequestNumber,
        int quotationNo,
        UtcInstant committedAt) {

    public BookingSummary {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(committedAt, "committedAt");
    }
}
