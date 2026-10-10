package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.queryservices.BookingConfirmationPage;
import com.example.cargotracker.booking.application.internal.queryservices.BookingDetail;
import com.example.cargotracker.booking.application.internal.queryservices.RecentBookings;
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
        return transportRequestNumber + " " + quotation(quotationNo);
    }

    /** 見積りの表記（「見積 1」。S-10・S-24 の件名と C-06 の見積依頼の列で同じ）。 */
    static String quotation(int quotationNo) {
        return "見積 " + quotationNo;
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
                trackingStartPending(detail.sagaStatus()));
    }

    /**
     * 追跡の開始の表示。UI 設計の共通部品「処理中表示」の言葉（処理中・完了・有人確認要）で示し、処理中を完了と示さない（ADR-015）。
     * 失敗は「処理中」のまま示し、有人確認要で受付番号を示す（UI 設計。Bolt 25b の確認ポイント 6）。失敗・有人確認要は W8 で起きる。
     */
    static String trackingStart(BookingSagaStatus status) {
        return switch (status) {
            case IN_PROGRESS, FAILED -> "処理中";
            case COMPLETED -> "完了";
            case NEEDS_HUMAN -> "有人確認要（担当者が確認します）";
        };
    }

    /** 追跡の開始を「処理中」と示すか（失敗も処理中と示す）。S-24 は処理中のときだけ更新の案内を出す（Bolt 25b レビュー P-3）。 */
    static boolean trackingStartPending(BookingSagaStatus status) {
        return status == BookingSagaStatus.IN_PROGRESS || status == BookingSagaStatus.FAILED;
    }

    /**
     * S-10 予約一覧の 1 行の表示（Bolt 25b）。
     *
     * @param trackingNumber 追跡番号
     * @param quotation 見積りの表記（業務番号と見積り番号）
     * @param committedAt 確定時刻（最初の確定の時刻）
     * @param trackingStart 追跡の開始
     */
    record ListRow(String trackingNumber, String quotation, String committedAt, String trackingStart) {}

    static List<ListRow> list(RecentBookings recent) {
        return recent.rows().stream()
                .map(row -> new ListRow(
                        row.trackingNumber().value(),
                        subject(row.transportRequestNumber(), row.quotationNo()),
                        staff(row.committedAt()),
                        trackingStart(row.sagaStatus())))
                .toList();
    }

    // C-06 予約一覧（荷主。Bolt 27b）。日時は荷主の日時表示（UTC を併記しない）、予約サガの状態は荷主の語で示す

    /**
     * C-06 予約一覧の 1 行の表示。
     *
     * @param trackingNumber 追跡番号
     * @param tracked 追跡が始まっているか（追跡番号を C-10 の照会の結果へのリンクにする）
     * @param transportRequestNumber 業務番号（C-04 へのリンクにする）
     * @param quotation 見積りの表記（「見積 1」）
     * @param committedAt 確定時刻（最初の確定の時刻）
     * @param tracking 追跡の表示
     */
    record CustomerListRow(
            String trackingNumber,
            boolean tracked,
            String transportRequestNumber,
            String quotation,
            String committedAt,
            String tracking) {}

    static List<CustomerListRow> customerList(RecentBookings recent) {
        return recent.rows().stream()
                .map(row -> new CustomerListRow(
                        row.trackingNumber().value(),
                        tracked(row.sagaStatus()),
                        row.transportRequestNumber(),
                        quotation(row.quotationNo()),
                        DateTimeDisplay.customer(row.committedAt().instant()),
                        customerTracking(row.sagaStatus())))
                .toList();
    }

    /**
     * 荷主に示す追跡の表示（確認ポイント 4）。社内の「追跡の開始」（処理中・完了・有人確認要）を、貨物の側の状態として荷主の語で示す。
     * 失敗は共通部品「処理中表示」と同じく利用者には開始待ちと示し、有人確認要は人が確認することを示す。「準備中」は準備中の画面の語なので
     * 使わない。
     */
    static String customerTracking(BookingSagaStatus status) {
        return switch (status) {
            case IN_PROGRESS, FAILED -> "追跡の開始待ち";
            case COMPLETED -> "追跡中";
            case NEEDS_HUMAN -> "追跡の開始待ち（担当営業が確認します）";
        };
    }

    /** 追跡が始まっているか（荷主の一覧で追跡番号を C-10 の照会の結果へのリンクにする）。予約サガの完了だけ。 */
    static boolean tracked(BookingSagaStatus status) {
        return status == BookingSagaStatus.COMPLETED;
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
