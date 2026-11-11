package com.example.cargotracker.booking.interfaces.api;

import java.util.Objects;

/**
 * 輸送段階の通知の受領（予約の公開 API。Bolt 39g2）。{@link TrackingStartNotificationReceipt} と同じ形。
 */
public sealed interface TransportPhaseNotificationReceipt {

    /** 予約の状態と輸送段階を進めた。 */
    record Recorded() implements TransportPhaseNotificationReceipt {}

    /** すでに記録済み（同じ通知の再通知か、後から届いた古い通知。何もしない）。 */
    record AlreadyRecorded() implements TransportPhaseNotificationReceipt {}

    /**
     * 記録しなかった（再配信で直らない理由）。
     *
     * @param reason 理由の名前（このレコードの定数のどれか。公開 API の契約で、予約のドメインの値の名前とは独立に決める）
     */
    record NotRecorded(String reason) implements TransportPhaseNotificationReceipt {

        /** 予約がない。 */
        public static final String BOOKING_NOT_FOUND = "BOOKING_NOT_FOUND";

        /** 取消済みの予約。 */
        public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";

        /** 変更・取消しの申請中の予約（W11 で決め直す）。 */
        public static final String BOOKING_UNDER_REQUEST = "BOOKING_UNDER_REQUEST";

        public NotRecorded {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
