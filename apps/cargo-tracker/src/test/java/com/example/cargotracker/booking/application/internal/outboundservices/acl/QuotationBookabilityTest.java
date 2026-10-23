package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 見積りの公開 API の使えない理由を、予約の理由に写す（ADR-016）。再設計要は名前どおりに写し、予約の知らない理由は未承認に寄せる
 * （Bolt 37e3 の状態の組み合わせの行 9・13）。
 */
class QuotationBookabilityTest {

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-10-09T01:00:00Z"));

    @Test
    void 割り当てた経路が再設計要なら再設計要と写す() {
        // 行 9
        assertThat(checkWith(BookableQuotationResult.NotBookable.ROUTE_REDESIGN_REQUIRED))
                .isEqualTo(
                        new QuotationBookability.Result.Unavailable(QuotationUnavailability.ROUTE_REDESIGN_REQUIRED));
    }

    @Test
    void 本予約の確定でも再設計要と写す() {
        QuotationBookability bookability = new QuotationBookability(new RecordingBookableQuotationQuery(request ->
                new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.ROUTE_REDESIGN_REQUIRED)));

        assertThat(bookability.checkForConfirmation("TR-2026-0001", 1, AT))
                .isEqualTo(
                        new QuotationBookability.Result.Unavailable(QuotationUnavailability.ROUTE_REDESIGN_REQUIRED));
    }

    @Test
    void 予約の知らない理由は未承認に寄せる() {
        // 行 13（見積りが理由を足しても S-09 を 500 にしない。開発者が調べる）
        assertThat(checkWith("SOMETHING_NEW"))
                .isEqualTo(new QuotationBookability.Result.Unavailable(QuotationUnavailability.NOT_APPROVED));
    }

    private static QuotationBookability.Result checkWith(String reason) {
        return new QuotationBookability(
                        new RecordingBookableQuotationQuery(request -> new BookableQuotationResult.NotBookable(reason)))
                .check("TR-2026-0001", 1, AT);
    }
}
