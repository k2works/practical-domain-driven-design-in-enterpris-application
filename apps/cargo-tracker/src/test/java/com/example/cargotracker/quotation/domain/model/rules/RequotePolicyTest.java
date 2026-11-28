package com.example.cargotracker.quotation.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 再見積りできない理由の規則（D-85。Bolt 35c）。ボタン・GET・POST が同じ順で同じ理由を示す: 輸送要求の状態（予約確定済み → 見積り作成中・
 * 見積提示済み・経路設計中・荷主承認待ち・予約待ちでない）→ 版 → 見積りの判定時刻の判定。
 */
class RequotePolicyTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-07T05:00:00Z"));
    private static final UtcInstant EXPIRES = QuotationFixture.EXPIRES_AT;

    private final UserId staff = new UserId(UUID.randomUUID());
    private final TransportRequest request = TransportRequest.submit(
            new TransportRequestId(UUID.randomUUID()),
            new TransportRequestNumber(2026, 1),
            new CompanyId(UUID.randomUUID()),
            ShipmentTermsFixture.generalCargo(),
            staff,
            NOW);

    @Test
    void 予約待ちの見積依頼の承認済みの見積りは期限を過ぎれば再見積りでき期限内は拒む() {
        readyToBook();
        Quotation approved = approved();

        assertThat(RequotePolicy.rejection(request, approved, EXPIRES)).isEmpty();
        assertThat(RequotePolicy.rejection(
                        request, approved, new UtcInstant(EXPIRES.instant().minusSeconds(60))))
                .contains(QuotationRejection.APPROVED_AND_STILL_VALID);
    }

    @Test
    void 予約確定済みの見積依頼の見積りは期限を過ぎても予約確定済みの理由で拒む() {
        readyToBook();
        request.markBooked(1);

        assertThat(RequotePolicy.rejection(request, approved(), EXPIRES))
                .contains(QuotationRejection.TRANSPORT_REQUEST_BOOKED);
    }

    @Test
    void 予約確定済みの見積依頼の期限内の承認済みの見積りも見積りの判定より先に予約確定済みの理由で拒む() {
        readyToBook();
        request.markBooked(1);

        assertThat(RequotePolicy.rejection(request, approved(), NOW))
                .as("見積りの判定（期限内の承認済み）より輸送要求の状態を先に見る")
                .contains(QuotationRejection.TRANSPORT_REQUEST_BOOKED);
    }

    @Test
    void 審査中の見積依頼の古い版の見積りは版より先に輸送要求の状態の理由で拒む() {
        request.sendBack(1, staff, "目的地を直してください", "", NOW);
        request.resubmit(ShipmentTermsFixture.generalCargo(), staff, NOW);
        Quotation old = pending(1);

        assertThat(RequotePolicy.rejection(request, old, NOW))
                .contains(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING);
    }

    @Test
    void 審査中の見積依頼の見積りは見積り作成中でない理由で拒む() {
        Quotation pending = pending(1);

        assertThat(RequotePolicy.rejection(request, pending, NOW))
                .contains(QuotationRejection.TRANSPORT_REQUEST_NOT_QUOTING);
    }

    @Test
    void 古い版の見積りは輸送要求の状態の次に版の理由で拒む() {
        request.sendBack(1, staff, "目的地を直してください", "", NOW);
        request.resubmit(ShipmentTermsFixture.generalCargo(), staff, NOW);
        request.approve(2, staff, "根拠", NOW);
        Quotation old = pending(1);

        assertThat(RequotePolicy.rejection(request, old, NOW)).contains(QuotationRejection.OUTDATED_VERSION);
    }

    @Test
    void 見積り作成中の見積依頼の承認待ちの見積りは再見積りでき置換済みは拒む() {
        request.approve(1, staff, "根拠", NOW);
        Quotation pending = pending(1);
        Quotation replaced = pending(1);
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), NOW);

        assertThat(RequotePolicy.rejection(request, pending, NOW)).isEmpty();
        assertThat(RequotePolicy.rejection(request, replaced, NOW)).contains(QuotationRejection.REPLACED);
    }

    private void readyToBook() {
        request.approve(1, staff, "根拠", NOW);
        request.markQuotationPresented(1);
        request.markRoutingRequested(1);
        request.markAwaitingApproval(1);
        request.markReadyToBook(1);
    }

    private Quotation pending(int versionNo) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), versionNo, 1);
        quotation.calculate(QuotationFixture.completeInput(), NOW);
        return quotation;
    }

    private Quotation approved() {
        Quotation quotation = pending(1);
        quotation.presentInternally(staff, NOW);
        quotation.requestRouteDesign(staff, NOW);
        quotation.assignRoute(QuotationFixture.assignedRoute(), NOW);
        quotation.approveByShipper(staff, NOW);
        return quotation;
    }
}
