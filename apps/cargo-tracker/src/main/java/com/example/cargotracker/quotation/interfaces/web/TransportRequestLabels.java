package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.quotation.application.internal.queryservices.CargoDisplayNames;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 見積依頼の画面（荷主の C-02・C-03・C-04、社内の S-02・S-03）で共通に使う表示名と日時の書式。
 */
final class TransportRequestLabels {

    // 名前は見積りの application 層の CargoDisplayNames に 1 か所で置く（予約へ渡す要約と同じ名前にする。Bolt 29c）
    static final List<Option> CARGO_CATEGORIES = Arrays.stream(CargoCategory.values())
            .map(category -> new Option(category.name(), CargoDisplayNames.category(category)))
            .toList();

    static final List<Option> PACKAGE_TYPES = Arrays.stream(PackageType.values())
            .map(packageType -> new Option(packageType.name(), CargoDisplayNames.packageType(packageType)))
            .toList();

    private TransportRequestLabels() {}

    /** 荷主の画面の日時（例: 2026-10-05 10:00 Asia/Tokyo（UTC+09:00））。platform の部品（Bolt 22）。 */
    static String customerDateTime(UtcInstant instant) {
        return DateTimeDisplay.customer(instant.instant());
    }

    /** 社内の画面の日時。利用者のタイムゾーンを主にし、UTC を括弧で併記する（UI 設計の共通部品「日時表示」。Bolt 22）。 */
    static String staffDateTime(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }

    /**
     * 荷主の画面の状態の表示名。いま誰の対応待ちかを添える（UI 設計 C-04 の対応表。Bolt 6）。
     * 社内の画面の表示名（{@link #status(TransportRequestStatus)}）は変えない。
     */
    static String customerStatus(TransportRequestStatus status) {
        return switch (status) {
            case DRAFT -> "差戻し（お客様の対応待ち）";
            case UNDER_REVIEW -> "審査中（A 社の対応待ち）";
            case QUOTING -> "見積り作成中（A 社の対応待ち）";
            case QUOTED -> "見積提示済み（お客様の対応待ち）";
            case ROUTING -> "経路設計中（A 社の対応待ち）";
            case AWAITING_APPROVAL -> "見積りと経路の承認待ち（お客様の対応待ち）";
            case READY_TO_BOOK -> "予約待ち（A 社の対応待ち）";
            case BOOKED -> "予約確定済み";
        };
    }

    /** 承認の後に失効した見積りが最新のときの案内（D-85。Bolt 35d）。 */
    static final String REQUOTING_AFTER_APPROVAL = "承認の後に見積りの有効期限を過ぎました。担当営業が新しい見積りを用意しています。";

    /** 再承認の見積りが荷主承認待ちで有効なときの案内（するべきこと。見積りの節は事実を示す。D-85。Bolt 35d の開発レビュー）。 */
    static final String REAPPROVAL = "料金と有効期限を出し直した見積りが届きました。もう一度承認してください。";

    /** 割り当てた経路が再設計要の荷主承認待ちの案内（社内の語を出さない。US-07 AC5。Bolt 37e3）。 */
    static final String ROUTE_REVIEW_BEFORE_APPROVAL = "割り当てた経路の見直しが必要になったため、この見積りと経路は承認できません。担当営業にご相談ください。";

    /** 割り当てた経路が再設計要の承認済みの案内（本予約の確定は案内しない。US-07 AC5。Bolt 37e3）。 */
    static final String ROUTE_REVIEW_AFTER_APPROVAL = "割り当てた経路の見直しが必要になりました。本予約の扱いは担当営業からご連絡します。";

    /**
     * 荷主の画面の状態の表示名と案内（C-02・C-04）。
     *
     * @param status 状態の表示名
     * @param guidance 案内
     */
    record CustomerStage(String status, String guidance) {}

    /**
     * 荷主に見える最新の見積りの、状態の選び方に使う値（業務の判定は見積りの集約・読み取りモデルが決めた値を渡す）。
     *
     * @param status 見積りの状態
     * @param expired 表示する時刻で失効しているか（失効の記録を含む）
     * @param shipperApproved 荷主が承認した見積りか（承認の後に失効したものを含む）
     * @param reapproval 再承認の見積りか
     * @param routeRedesignRequired 割り当てた経路が再設計要か（US-07 AC5。Bolt 37e3）
     */
    record LatestQuotation(
            QuotationStatus status,
            boolean expired,
            boolean shipperApproved,
            boolean reapproval,
            boolean routeRedesignRequired) {}

    /**
     * 荷主の画面の状態の表示名と案内（C-02・C-04 で共通。D-85。Bolt 35d）。輸送要求が荷主承認待ち・予約待ちのときは、輸送要求の状態の更新
     * （DE-24・DE-04 の受け取り）を待たずに、荷主に見える最新の見積りから選ぶ（見積りの状態を正にする。Bolt 35c）。表示名は今ある言葉だけを
     * 使う。承認の後に失効した見積りは、A 社が新しい見積りを用意している段階として「見積り作成中（A 社の対応待ち）」で示す。
     */
    static CustomerStage customerStage(TransportRequestStatus status, Optional<LatestQuotation> latest) {
        if (status != TransportRequestStatus.AWAITING_APPROVAL && status != TransportRequestStatus.READY_TO_BOOK
                || latest.isEmpty()) {
            return stageOf(status);
        }
        return latestQuotationStage(latest.get(), status);
    }

    /** 荷主に見える最新の見積りから選ぶ（失効、割り当てた経路の再設計要、見積りの状態の順に見る）。 */
    private static CustomerStage latestQuotationStage(LatestQuotation quotation, TransportRequestStatus status) {
        if (quotation.shipperApproved() && quotation.expired()) {
            return new CustomerStage(customerStatus(TransportRequestStatus.QUOTING), REQUOTING_AFTER_APPROVAL);
        }
        if (quotation.routeRedesignRequired() && !quotation.expired()) {
            return routeReviewStage(quotation.status(), status);
        }
        return switch (quotation.status()) {
            case APPROVED -> stageOf(TransportRequestStatus.READY_TO_BOOK);
            case AWAITING_SHIPPER_APPROVAL -> awaitingApprovalStage(quotation);
            // 見積りの状態を足したら（D-86、荷主の辞退）ここで扱いを決める（ADR-018）
            case DRAFT, PENDING_APPROVAL, PRESENTED, ROUTING_REQUESTED, EXPIRED, REPLACED -> stageOf(status);
        };
    }

    /** 割り当てた経路が再設計要の見積り。状態の表示名は今のまま、案内だけを担当営業への相談に替える（Bolt 37e3）。 */
    private static CustomerStage routeReviewStage(
            QuotationStatus quotationStatus, TransportRequestStatus requestStatus) {
        return switch (quotationStatus) {
            case APPROVED ->
                new CustomerStage(customerStatus(TransportRequestStatus.READY_TO_BOOK), ROUTE_REVIEW_AFTER_APPROVAL);
            case AWAITING_SHIPPER_APPROVAL ->
                new CustomerStage(
                        customerStatus(TransportRequestStatus.AWAITING_APPROVAL), ROUTE_REVIEW_BEFORE_APPROVAL);
            case DRAFT, PENDING_APPROVAL, PRESENTED, ROUTING_REQUESTED, EXPIRED, REPLACED -> stageOf(requestStatus);
        };
    }

    /** 荷主承認待ちの見積り。再承認の見積りで有効なら、もう一度の承認を求める（D-86 の失効は今の荷主承認待ちの案内のまま）。 */
    private static CustomerStage awaitingApprovalStage(LatestQuotation quotation) {
        return quotation.reapproval() && !quotation.expired()
                ? new CustomerStage(customerStatus(TransportRequestStatus.AWAITING_APPROVAL), REAPPROVAL)
                : stageOf(TransportRequestStatus.AWAITING_APPROVAL);
    }

    private static CustomerStage stageOf(TransportRequestStatus status) {
        return new CustomerStage(customerStatus(status), customerGuidance(status));
    }

    /** 荷主の画面で、いま誰が何をしているか、荷主が次に何をするかの案内。 */
    static String customerGuidance(TransportRequestStatus status) {
        return switch (status) {
            case DRAFT -> "営業担当者が見積依頼を差し戻しました。差戻しの理由を確かめ、直して出し直してください。";
            case UNDER_REVIEW -> "営業担当者が内容を審査しています。お問い合わせの際は業務番号をお伝えください。";
            case QUOTING -> "営業担当者が見積りを作成しています。お問い合わせの際は業務番号をお伝えください。";
            case QUOTED -> "見積りと経路方針を提示しました。料金根拠と有効期限を確かめてください。";
            case ROUTING -> "経路設計者が詳細な経路を設計しています。承認の準備ができたら担当営業からご連絡します。";
            case AWAITING_APPROVAL -> "経路が確定しました。見積りと経路を確かめて承認してください。";
            case READY_TO_BOOK -> "見積りと経路を承認いただきました。担当営業が本予約を確定します。確定したらご連絡します。";
            // C-04 は予約一覧へのリンクを文の中に置くため、この文をテンプレートで組み立てる（detail.html。Bolt 29b）
            case BOOKED -> "本予約を確定しました。追跡番号と追跡の状況は予約一覧で確かめられます。";
        };
    }

    static String status(TransportRequestStatus status) {
        return switch (status) {
            case DRAFT -> "差戻し（下書き）";
            case UNDER_REVIEW -> "審査中";
            case QUOTING -> "見積り作成中";
            case QUOTED -> "見積提示済み";
            case ROUTING -> "経路設計中";
            case AWAITING_APPROVAL -> "荷主承認待ち";
            case READY_TO_BOOK -> "荷主承認済み（予約待ち）";
            case BOOKED -> "予約確定済み";
        };
    }

    /** 業務番号と版の表記（例: TR-2026-0001 版 1）。電話やメールでそのまま伝えられる形にする（D-4）。 */
    static String numberWithVersion(TransportRequestNumber number, int versionNo) {
        return number.text() + " 版 " + versionNo;
    }

    static String decision(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> "充足（審査を確定）";
            case SENT_BACK -> "差戻し";
        };
    }

    static String cargoCategory(CargoCategory category) {
        return CargoDisplayNames.category(category);
    }

    static String packageType(PackageType packageType) {
        return CargoDisplayNames.packageType(packageType);
    }

    /**
     * 選択の欄の 1 つの選択肢。
     *
     * @param value 送る値（ドメインの定数名）
     * @param label 画面に出す名前
     */
    public record Option(String value, String label) {}

    /** 不足事項の表示。なければ（空・空白だけを含む）「（なし）」とする（Bolt 6〜8 レビュー R-32）。 */
    static String missingItems(String missingItems) {
        return missingItems == null || missingItems.isBlank() ? "（なし）" : missingItems;
    }
}
