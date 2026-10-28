package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import java.util.List;
import java.util.Objects;

/**
 * 本予約の確定の結果（US-04 AC1〜AC5。Bolt 23・24・35）。画面が結果ごとの文言を示す（Bolt 23b）。
 */
public sealed interface BookingConfirmationOutcome {

    /**
     * 確定した。予約サガは処理中で、追跡の開始を待つ（ADR-015）。同じコマンド ID の再送にも最初の結果としてこれを返す（B-INV-03）。
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
     * 確定条件が欠けている（B-INV-01）。欠けた条件はすべて返し、画面がまとめて示す（US-04 AC3。Bolt 35）。
     *
     * @param missing 欠けた条件
     */
    record MissingConditions(List<BookingCondition> missing) implements BookingConfirmationOutcome {

        public MissingConditions {
            missing = List.copyOf(missing);
        }
    }

    /**
     * 同じ見積りの予約がすでにある（別のコマンド ID・別の利用者の確定。B-INV-11。Bolt 24）。
     *
     * @param trackingNumber 既存の予約の追跡番号
     */
    record AlreadyBooked(TrackingNumber trackingNumber) implements BookingConfirmationOutcome {

        public AlreadyBooked {
            Objects.requireNonNull(trackingNumber, "trackingNumber");
        }
    }

    /**
     * 見積りが特殊貨物（危険物・冷凍貨物・その他特殊貨物）を含む（B-INV-04、US-04 AC5。Bolt 35）。MVP の対象外で、直す欄がないので
     * 確定条件の不足とは分ける。
     */
    record SpecialCargo() implements BookingConfirmationOutcome {}

    /** 同じコマンド ID で内容（業務番号・見積り番号・操作者）が違う（衝突。B-INV-03。Bolt 24）。 */
    record CommandConflict() implements BookingConfirmationOutcome {}

    /** 営業担当者でない（B-INV-10。カスタマーサポートは確定できない）。 */
    record Forbidden() implements BookingConfirmationOutcome {}

    /**
     * 見積りの輸送要求が操作者の担当企業の外（Bolt 38f2。US-16 AC4）。画面は試行を記録して 404 にする。controller が確定の前に範囲を
     * 確かめるので、これはその後に担当企業が変わったときにだけ返る。
     *
     * @param transportRequestNumber 正規の業務番号（記録の ID に使う）
     * @param quotationNo 見積り番号
     */
    record OutOfScope(String transportRequestNumber, int quotationNo) implements BookingConfirmationOutcome {

        public OutOfScope {
            Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        }
    }
}
