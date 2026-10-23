package com.example.cargotracker.quotation.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 荷主の画面の状態と案内の選び方（C-02・C-04。UI 設計の組み合わせの表。D-85。Bolt 35d）。輸送要求が荷主承認待ち・予約待ちのときは荷主に
 * 見える最新の見積りから選び、ほかの状態では輸送要求から選ぶ。
 */
class TransportRequestLabelsCustomerStageTest {

    private static final String AWAITING = "見積りと経路の承認待ち（お客様の対応待ち）";
    private static final String READY = "予約待ち（A 社の対応待ち）";
    private static final String QUOTING = "見積り作成中（A 社の対応待ち）";

    @ParameterizedTest(name = "[{index}] 輸送要求 {0} × 見積り {1}（失効 {2}・承認 {3}・再承認 {4}）→ {5}")
    @CsvSource({
        // 行 1: 通常の荷主承認待ち
        "AWAITING_APPROVAL, AWAITING_SHIPPER_APPROVAL, false, false, false, " + AWAITING + ", AWAITING_APPROVAL",
        // 行 2: DE-04 の受け取りの前（通常・再承認）
        "AWAITING_APPROVAL, APPROVED, false, true, false, " + READY + ", READY_TO_BOOK",
        "AWAITING_APPROVAL, APPROVED, false, true, true, " + READY + ", READY_TO_BOOK",
        // 行 3・9: 承認済み（通常・再承認）
        "READY_TO_BOOK, APPROVED, false, true, false, " + READY + ", READY_TO_BOOK",
        "READY_TO_BOOK, APPROVED, false, true, true, " + READY + ", READY_TO_BOOK",
        // 行 4・5: 承認の後に失効（時刻で失効・失効の記録）
        "READY_TO_BOOK, APPROVED, true, true, false, " + QUOTING + ", REQUOTING",
        "READY_TO_BOOK, EXPIRED, true, true, false, " + QUOTING + ", REQUOTING",
        "AWAITING_APPROVAL, APPROVED, true, true, false, " + QUOTING + ", REQUOTING",
        // 行 6・7: 再承認の荷主承認待ち（DE-24 の前後）
        "READY_TO_BOOK, AWAITING_SHIPPER_APPROVAL, false, false, true, " + AWAITING + ", REAPPROVAL",
        "AWAITING_APPROVAL, AWAITING_SHIPPER_APPROVAL, false, false, true, " + AWAITING + ", REAPPROVAL",
        // 行 8: 荷主承認待ちで失効（通常・再承認。D-86）
        "AWAITING_APPROVAL, AWAITING_SHIPPER_APPROVAL, true, false, false, " + AWAITING + ", AWAITING_APPROVAL",
        "READY_TO_BOOK, AWAITING_SHIPPER_APPROVAL, true, false, true, " + AWAITING + ", AWAITING_APPROVAL",
    })
    void 荷主承認待ちと予約待ちの見積依頼は最新の見積りから状態と案内を選ぶ(
            TransportRequestStatus request,
            QuotationStatus quotation,
            boolean expired,
            boolean approved,
            boolean reapproval,
            String status,
            String guidance) {
        TransportRequestLabels.CustomerStage stage = TransportRequestLabels.customerStage(
                request,
                Optional.of(
                        new TransportRequestLabels.LatestQuotation(quotation, expired, approved, reapproval, false)));

        assertThat(stage.status()).isEqualTo(status);
        assertThat(stage.guidance()).isEqualTo(guidance(guidance));
    }

    @ParameterizedTest
    @EnumSource(
            names = {"AWAITING_APPROVAL", "READY_TO_BOOK"},
            mode = EnumSource.Mode.EXCLUDE)
    void ほかの状態の見積依頼は最新の見積りによらず輸送要求から選ぶ(TransportRequestStatus request) {
        TransportRequestLabels.CustomerStage stage = TransportRequestLabels.customerStage(
                request,
                Optional.of(
                        new TransportRequestLabels.LatestQuotation(QuotationStatus.APPROVED, true, true, true, false)));

        assertThat(stage.status()).isEqualTo(TransportRequestLabels.customerStatus(request));
        assertThat(stage.guidance()).isEqualTo(TransportRequestLabels.customerGuidance(request));
    }

    @Test
    void 荷主に見える見積りがなければ輸送要求から選ぶ() {
        assertThat(TransportRequestLabels.customerStage(TransportRequestStatus.READY_TO_BOOK, Optional.empty()))
                .isEqualTo(new TransportRequestLabels.CustomerStage(
                        READY, TransportRequestLabels.customerGuidance(TransportRequestStatus.READY_TO_BOOK)));
    }

    private static String guidance(String key) {
        return switch (key) {
            case "REQUOTING" -> TransportRequestLabels.REQUOTING_AFTER_APPROVAL;
            case "REAPPROVAL" -> TransportRequestLabels.REAPPROVAL;
            default -> TransportRequestLabels.customerGuidance(TransportRequestStatus.valueOf(key));
        };
    }

    // 割り当てた経路が再設計要の見積り（US-07 AC5。Bolt 37e3 の状態の組み合わせの行 1〜3）

    @Test
    void 割り当てた経路が再設計要の荷主承認待ちは承認を求めず担当営業への相談を案内する() {
        // 行 1
        assertThat(stage(
                        TransportRequestStatus.AWAITING_APPROVAL,
                        QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                        false,
                        true))
                .isEqualTo(new TransportRequestLabels.CustomerStage(
                        AWAITING, "割り当てた経路の見直しが必要になったため、この見積りと経路は承認できません。担当営業にご相談ください。"));
    }

    @Test
    void 割り当てた経路が再設計要の承認済みは本予約の確定を案内せず担当営業からの連絡を案内する() {
        // 行 2
        assertThat(stage(TransportRequestStatus.READY_TO_BOOK, QuotationStatus.APPROVED, false, true))
                .isEqualTo(
                        new TransportRequestLabels.CustomerStage(READY, "割り当てた経路の見直しが必要になりました。本予約の扱いは担当営業からご連絡します。"));
    }

    @Test
    void 割り当てた経路が再設計要でも失効していれば失効の案内を先に示す() {
        assertThat(stage(TransportRequestStatus.READY_TO_BOOK, QuotationStatus.APPROVED, true, true)
                        .guidance())
                .isEqualTo(TransportRequestLabels.REQUOTING_AFTER_APPROVAL);
    }

    @Test
    void 割り当てた経路が再設計要の荷主承認待ちが失効したら今の荷主承認待ちの案内のまま() {
        // D-86（荷主承認待ちの失効は今の案内）。担当営業が期限の後に再見積りする
        assertThat(stage(
                        TransportRequestStatus.AWAITING_APPROVAL,
                        QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                        true,
                        true))
                .isEqualTo(new TransportRequestLabels.CustomerStage(
                        AWAITING, TransportRequestLabels.customerGuidance(TransportRequestStatus.AWAITING_APPROVAL)));
    }

    @Test
    void 割り当てた経路が再設計要でも状態の表示名は変わらない() {
        // C-02 の一覧は表示名だけを使い、印を渡さない（Bolt 37e3 の開発レビュー）
        assertThat(stage(TransportRequestStatus.READY_TO_BOOK, QuotationStatus.APPROVED, false, true)
                        .status())
                .isEqualTo(stage(TransportRequestStatus.READY_TO_BOOK, QuotationStatus.APPROVED, false, false)
                        .status());
        assertThat(stage(
                                TransportRequestStatus.AWAITING_APPROVAL,
                                QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                                false,
                                true)
                        .status())
                .isEqualTo(stage(
                                TransportRequestStatus.AWAITING_APPROVAL,
                                QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                                false,
                                false)
                        .status());
    }

    @Test
    void 割り当てた経路が再設計要でなければ今の案内のまま() {
        // 行 3
        assertThat(stage(
                        TransportRequestStatus.AWAITING_APPROVAL,
                        QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                        false,
                        false))
                .isEqualTo(new TransportRequestLabels.CustomerStage(
                        AWAITING, TransportRequestLabels.customerGuidance(TransportRequestStatus.AWAITING_APPROVAL)));
    }

    private static TransportRequestLabels.CustomerStage stage(
            TransportRequestStatus request, QuotationStatus quotation, boolean expired, boolean routeRedesignRequired) {
        return TransportRequestLabels.customerStage(
                request,
                Optional.of(new TransportRequestLabels.LatestQuotation(
                        quotation, expired, quotation == QuotationStatus.APPROVED, false, routeRedesignRequired)));
    }
}
