package com.example.cargotracker.booking.interfaces.api;

import java.util.Objects;

/**
 * 追跡の開始の結果の通知の受領（予約の公開 API。Bolt 25）。見積りの公開 API の {@code BookingNotificationReceipt} と同じ形。
 */
public sealed interface TrackingStartNotificationReceipt {

    /** 処理中の予約サガを完了にした。 */
    record Completed() implements TrackingStartNotificationReceipt {}

    /** すでに完了していた（同じ結果の再通知。何もしない）。 */
    record AlreadyCompleted() implements TrackingStartNotificationReceipt {}

    /**
     * 完了にしなかった（再配信で直らない欠け）。
     *
     * @param reason 理由の名前（このレコードの定数のどれか。公開 API の契約で、予約のドメインの値の名前とは独立に決める）
     */
    record NotCompleted(String reason) implements TrackingStartNotificationReceipt {

        /** 予約サガがない。 */
        public static final String SAGA_NOT_FOUND = "SAGA_NOT_FOUND";

        /** 予約サガが処理中でも完了でもない（失敗・有人確認要）。遅れて届いた結果で状態を変えない。 */
        public static final String SAGA_NOT_IN_PROGRESS = "SAGA_NOT_IN_PROGRESS";

        public NotCompleted {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
