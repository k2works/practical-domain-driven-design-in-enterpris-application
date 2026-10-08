package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices.BookingConfirmationPage;
import com.example.cargotracker.booking.application.internal.queryservices.BookingDetail;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.entities.BookingVersion;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Optional;

/** S-09・S-24 の表示の値（社内の画面の日時は利用者のタイムゾーンと UTC を併記する。Bolt 22）。 */
final class BookingViews {

    private BookingViews() {}

    /** 見積りの表記（例: TR-2026-0001 見積 1）。 */
    static String subject(String transportRequestNumber, int quotationNo) {
        return transportRequestNumber + " 見積 " + quotationNo;
    }

    static Optional<TrackingNumber> parseTrackingNumber(String text) {
        try {
            return Optional.of(new TrackingNumber(text));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    /**
     * S-09 の表示。
     *
     * @param transportRequestNumber 業務番号
     * @param quotationNo 見積り番号
     * @param subject 見積りの表記
     * @param expiresAt 有効期限
     * @param cargoSummary 貨物の要約（空なら null）
     * @param shipperApprovedAt 荷主の承認時刻
     * @param route 承認済み経路版（案件番号と経路版）
     * @param cargoMissing 必須貨物情報が欠けているか
     * @param cargoRejected 送ったときに必須貨物情報が欠けていて確定しなかったか
     * @param staffConfirmationMissing 送ったときに営業担当者の確認がなかったか
     */
    record ConfirmationView(
            String transportRequestNumber,
            int quotationNo,
            String subject,
            String expiresAt,
            String cargoSummary,
            String shipperApprovedAt,
            String route,
            boolean cargoMissing,
            boolean cargoRejected,
            boolean staffConfirmationMissing) {

        /** エラー要約を出すか（送ったときに確定条件が欠けていた）。 */
        public boolean hasErrors() {
            return cargoRejected || staffConfirmationMissing;
        }
    }

    static ConfirmationView confirmation(
            String transportRequestNumber,
            int quotationNo,
            BookingConfirmationPage.Available page,
            List<BookingCondition> missing) {
        BookingTerms terms = page.terms();
        boolean cargoMissing = terms.cargoSummary().isBlank();
        return new ConfirmationView(
                transportRequestNumber,
                quotationNo,
                subject(transportRequestNumber, quotationNo),
                staff(page.expiresAt()),
                cargoMissing ? null : terms.cargoSummary(),
                staff(page.shipperApprovedAt()),
                route(terms),
                cargoMissing,
                missing.contains(BookingCondition.REQUIRED_CARGO),
                missing.contains(BookingCondition.STAFF_CONFIRMATION));
    }

    /**
     * S-24 の表示。
     *
     * @param trackingNumber 追跡番号
     * @param transportRequestNumber 業務番号
     * @param status 状態の表示名
     * @param versionNo 予約版の番号
     * @param committedAt 確定時刻（業務上の commit 時刻。ADR-016）
     * @param route 経路（案件番号と経路版）
     * @param cargoSummary 貨物の要約
     * @param trackingStart 追跡の開始（予約サガの状態）
     * @param trackingStartPending 追跡の開始が処理中か（完了の知らせ方を案内する）
     */
    record DetailView(
            String trackingNumber,
            String transportRequestNumber,
            String status,
            int versionNo,
            String committedAt,
            String route,
            String cargoSummary,
            String trackingStart,
            boolean trackingStartPending) {}

    static DetailView detail(BookingDetail detail) {
        Booking booking = detail.booking();
        BookingVersion version = booking.currentVersion();
        return new DetailView(
                booking.trackingNumber().value(),
                booking.transportRequestNumber(),
                status(booking.status()),
                version.versionNo(),
                staff(version.committedAt()),
                route(version.terms()),
                version.terms().cargoSummary(),
                trackingStart(detail.sagaStatus()),
                detail.sagaStatus() == BookingSagaStatus.IN_PROGRESS);
    }

    /** 追跡の開始の表示。処理中を完了と示さない（ADR-015）。完了は Bolt 25、失敗・有人確認要は W8 で起きる。 */
    static String trackingStart(BookingSagaStatus status) {
        return switch (status) {
            case IN_PROGRESS -> "処理中（追跡の開始を待っています）";
            case COMPLETED -> "完了";
            case FAILED -> "失敗（担当者が確認します）";
            case NEEDS_HUMAN -> "有人確認要（担当者が確認します）";
        };
    }

    static String status(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "確定済み";
            case AMENDMENT_PENDING -> "変更申請中";
            case CANCELLATION_PENDING -> "取消申請中";
            case AMENDING -> "変更中";
            case CANCELLED -> "取消済み";
            case IN_TRANSIT -> "輸送中";
            case COMPLETED -> "輸送完了";
        };
    }

    private static String route(BookingTerms terms) {
        return terms.routingCaseNumber() + " 版 " + terms.routeVersionNo();
    }

    private static String staff(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }
}
