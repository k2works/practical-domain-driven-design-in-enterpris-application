package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 荷主が見積りを辞退したことを受けて、輸送要求を辞退で終了にする（DE-17 の受け取り。US-24 AC2。W9 の Bolt 48-1。台帳 D-173）。
 * 見積り作成中・見積提示済みで、辞退した見積りの対象の版が現在の版のときだけ終了にする。ほかのときは変えない（冪等）。
 */
class TransportRequestWithdrawnTest {

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-10-06T02:00:00Z"));
    private static final UserId STAFF = new UserId(UUID.randomUUID());

    private static TransportRequest underReview() {
        return TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                AT);
    }

    private static TransportRequest quoting() {
        TransportRequest request = underReview();
        request.approve(1, STAFF, "根拠", AT);
        return request;
    }

    @Test
    void 見積提示済みの輸送要求は辞退で終了になる() {
        TransportRequest request = quoting();
        request.markQuotationPresented(1);

        assertThat(request.markWithdrawn(1)).isTrue();

        assertThat(request.status()).isEqualTo(TransportRequestStatus.WITHDRAWN);
    }

    @Test
    void 見積提示の通知より先に辞退が届いても見積り作成中から辞退で終了にし_遅れた提示では戻らない() {
        TransportRequest request = quoting();

        assertThat(request.markWithdrawn(1)).isTrue();
        assertThat(request.markQuotationPresented(1)).isFalse();

        assertThat(request.status()).isEqualTo(TransportRequestStatus.WITHDRAWN);
    }

    @Test
    void 版が違う辞退と経路設計中以降と審査中は変えない() {
        TransportRequest outdated = quoting();
        outdated.markQuotationPresented(1);
        TransportRequest routing = quoting();
        routing.markRoutingRequested(1);
        TransportRequest review = underReview();

        assertThat(outdated.markWithdrawn(2)).isFalse();
        assertThat(routing.markWithdrawn(1)).isFalse();
        assertThat(review.markWithdrawn(1)).isFalse();

        assertThat(outdated.status()).isEqualTo(TransportRequestStatus.QUOTED);
        assertThat(routing.status()).isEqualTo(TransportRequestStatus.ROUTING);
        assertThat(review.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
    }

    @Test
    void 再配信は変えずすでに辞退で終了かを答える() {
        TransportRequest request = quoting();
        request.markWithdrawn(1);

        assertThat(request.markWithdrawn(1)).isFalse();
        assertThat(request.isWithdrawn(1)).isTrue();
        assertThat(request.isWithdrawn(2)).isFalse();
    }
}
