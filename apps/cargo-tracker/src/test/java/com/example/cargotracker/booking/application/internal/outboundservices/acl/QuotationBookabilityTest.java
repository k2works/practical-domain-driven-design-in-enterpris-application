package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 見積りの公開 API の使えない理由を、予約の理由に写す（ADR-016）。再設計要は名前どおりに写し、予約の知らない理由は未承認に寄せる
 * （Bolt 37e3 の状態の組み合わせの行 9・13）。範囲の外は理由に寄せず範囲の外と写し、操作者を公開 API に渡す（Bolt 38f2）。
 */
class QuotationBookabilityTest {

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-10-09T01:00:00Z"));
    private static final AuthenticatedActor OPERATOR =
            AuthenticatedActorFixture.salesHandling(new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()));

    @Test
    void 範囲の外は使えない理由ではなく範囲の外と写す() {
        // 行 3・4（V3。未承認の文言に化けさせない）
        QuotationBookability bookability = new QuotationBookability(new RecordingBookableQuotationQuery(
                request -> new BookableQuotationResult.OutOfScope("TR-2026-0001", 1)));

        assertThat(bookability.check("TR-2026-0001", 1, AT, OPERATOR))
                .isEqualTo(new QuotationBookability.Result.OutOfScope("TR-2026-0001", 1));
        assertThat(bookability.checkForConfirmation("tr-2026-0001", 1, AT, OPERATOR))
                .as("記録の ID には公開 API が返す正規の業務番号を使う")
                .isEqualTo(new QuotationBookability.Result.OutOfScope("TR-2026-0001", 1));
    }

    @Test
    void 操作者を公開APIの要求に載せる() {
        RecordingBookableQuotationQuery query = new RecordingBookableQuotationQuery(
                request -> new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.NOT_APPROVED));
        QuotationBookability bookability = new QuotationBookability(query);

        bookability.check("TR-2026-0001", 1, AT, OPERATOR);
        bookability.checkForConfirmation("TR-2026-0001", 1, AT, OPERATOR);

        assertThat(query.all())
                .containsExactly(
                        new BookableQuotationRequest("TR-2026-0001", 1, AT, OPERATOR),
                        new BookableQuotationRequest("TR-2026-0001", 1, AT, OPERATOR));
    }

    @Test
    void 承認済みでないなら未承認と写す() {
        assertThat(checkWith(BookableQuotationResult.NotBookable.NOT_APPROVED))
                .isEqualTo(new QuotationBookability.Result.Unavailable(QuotationUnavailability.NOT_APPROVED));
    }

    @Test
    void 割り当てた経路の最初の区間が出発済みなら出発済みと写す() {
        // Bolt 37e4 の状態の組み合わせの行 16
        assertThat(checkWith(BookableQuotationResult.NotBookable.ROUTE_ALREADY_DEPARTED))
                .isEqualTo(new QuotationBookability.Result.Unavailable(QuotationUnavailability.ROUTE_ALREADY_DEPARTED));
    }

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

        assertThat(bookability.checkForConfirmation("TR-2026-0001", 1, AT, OPERATOR))
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
                .check("TR-2026-0001", 1, AT, OPERATOR);
    }
}
