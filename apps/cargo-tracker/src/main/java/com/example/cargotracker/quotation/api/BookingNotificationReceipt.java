package com.example.cargotracker.quotation.api;

import java.util.Objects;

/**
 * 予約確定済みの通知の結果（見積りの公開 API。Bolt 23）。
 */
public sealed interface BookingNotificationReceipt {

    /** 輸送要求を予約確定済みにした。 */
    record Booked() implements BookingNotificationReceipt {}

    /** すでに予約確定済み（DE-07 の再配信。何もしなかった）。 */
    record AlreadyBooked() implements BookingNotificationReceipt {}

    /**
     * 予約確定済みにしなかった（業務の理由）。
     *
     * @param reason 理由の名前（このレコードの定数のどれか。公開 API の契約で、見積りのドメインの値の名前とは独立に決める）
     */
    record NotBooked(String reason) implements BookingNotificationReceipt {

        /** 輸送要求が見つからない。 */
        public static final String TRANSPORT_REQUEST_NOT_FOUND = "TRANSPORT_REQUEST_NOT_FOUND";

        /** 輸送要求が予約待ちでない、または版が現在の版でない。 */
        public static final String NOT_READY_TO_BOOK = "NOT_READY_TO_BOOK";

        public NotBooked {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
