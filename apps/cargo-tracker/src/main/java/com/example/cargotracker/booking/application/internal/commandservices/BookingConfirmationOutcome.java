package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import java.util.List;
import java.util.Objects;

/**
 * 本予約の確定の結果（US-04 AC1・AC2。Bolt 23）。画面が結果ごとの文言を示す（Bolt 23b）。
 */
public sealed interface BookingConfirmationOutcome {

    /**
     * 確定した。予約サガは処理中で、追跡の開始を待つ（ADR-015）。
     *
     * @param trackingNumber 発行した追跡番号
     */
    record Confirmed(TrackingNumber trackingNumber) implements BookingConfirmationOutcome {

        public Confirmed {
            Objects.requireNonNull(trackingNumber, "trackingNumber");
        }
    }

    /** commit 時刻が有効期限と同時刻以後で、見積りが失効した（US-04 AC2、BR-10）。再見積りが必要。 */
    record Expired() implements BookingConfirmationOutcome {}

    /**
     * 見積りが確定に使えない（失効のほか。見つからない・置換済み・荷主の承認済みでない）。
     *
     * @param reason 理由
     */
    record QuotationUnavailable(QuotationUnavailability reason) implements BookingConfirmationOutcome {

        public QuotationUnavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 確定条件が欠けている（B-INV-01）。不足条件の一覧の表示は W6（US-04 AC3）。
     *
     * @param missing 欠けた条件
     */
    record MissingConditions(List<BookingCondition> missing) implements BookingConfirmationOutcome {

        public MissingConditions {
            missing = List.copyOf(missing);
        }
    }

    /** 同じ見積りの予約がすでにある（B-INV-11）。既存の追跡番号を返す振る舞いは Bolt 24（B-INV-03）。 */
    record AlreadyBooked() implements BookingConfirmationOutcome {}

    /** 営業担当者でない（B-INV-10。カスタマーサポートは確定できない）。 */
    record Forbidden() implements BookingConfirmationOutcome {}
}
