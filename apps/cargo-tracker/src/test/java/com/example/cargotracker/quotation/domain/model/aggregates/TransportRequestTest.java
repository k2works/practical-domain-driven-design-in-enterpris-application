package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransportRequestTest {

    private final TransportRequestId id = new TransportRequestId(UUID.randomUUID());
    private final CompanyId shipper = new CompanyId(UUID.randomUUID());
    private final UserId submitter = new UserId(UUID.randomUUID());
    private final TransportRequestNumber number = new TransportRequestNumber(2026, 1);
    private final ShipmentTerms terms = ShipmentTermsFixture.generalCargo();
    private final UtcInstant now = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));

    @Test
    void 提出すると最初の版が審査中になり提出者と提出時刻が記録される() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        assertThat(request.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(request.number()).isEqualTo(number);
        assertThat(request.currentVersion().versionNo()).isEqualTo(1);
        assertThat(request.currentVersion().terms()).isEqualTo(terms);
        assertThat(request.currentVersion().submittedBy()).isEqualTo(submitter);
        assertThat(request.currentVersion().submittedAt()).isEqualTo(now);
    }

    @Test
    void 提出すると業務番号の表記を載せて輸送要求を提出したイベントを生成する() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        assertThat(request.domainEvents())
                .containsExactly(new TransportRequestSubmitted(id.value(), 1, shipper, now, "TR-2026-0001"));
    }

    @Test
    void イベントを消すと残らない() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        request.clearDomainEvents();

        assertThat(request.domainEvents()).isEmpty();
    }

    @Test
    void 取り出したイベントの一覧を変えても集約は変わらない() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        List<Object> events = request.domainEvents();

        assertThatThrownBy(events::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(request.domainEvents()).hasSize(1);
    }

    @Test
    void 現在の版の書類を版と書類番号で探せる() {
        RequiredDocument invoice = new RequiredDocument(
                1,
                DocumentType.COMMERCIAL_INVOICE,
                "i.pdf",
                DocumentMediaType.PDF,
                10,
                "0".repeat(64),
                "quotation/x/1");
        TransportRequest request =
                TransportRequest.submit(id, number, shipper, terms.withDocuments(List.of(invoice)), submitter, now);

        assertThat(request.document(1, 1)).contains(invoice);
        assertThat(request.document(1, 2)).isEmpty();
        assertThat(request.document(2, 1)).as("現在の版でない版の書類は見つからない").isEmpty();
    }

    @Test
    void 提出した集約と審査しただけの集約には読み込んだ後に作った版がない() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        assertThat(request.newVersion()).as("最初の版は保存（save）が書く").isEmpty();
        request.sendBack(1, submitter, "理由", "", now);
        assertThat(request.newVersion()).isEmpty();
    }

    @Test
    void 再提出すると読み込んだ後に作った版として新しい版を持つ() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);
        request.sendBack(1, submitter, "理由", "", now);

        request.resubmit(terms, submitter, now);

        assertThat(request.newVersion())
                .hasValueSatisfying(version -> assertThat(version.versionNo()).isEqualTo(2));
    }

    @Test
    void 見積り作成中の現在の版に見積りが提示されると見積提示済みになりほかでは変わらない() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        assertThat(request.markQuotationPresented(1)).as("審査中では変えない").isFalse();
        request.approve(1, submitter, "根拠", now);
        assertThat(request.markQuotationPresented(2)).as("現在の版でなければ変えない").isFalse();
        assertThat(request.markQuotationPresented(1)).isTrue();
        assertThat(request.status()).isEqualTo(TransportRequestStatus.QUOTED);
        assertThat(request.markQuotationPresented(1)).as("2 回目は変えない（冪等）").isFalse();
    }

    @Test
    void 見積提示済みか見積り作成中の現在の版に詳細経路設計が依頼されると経路設計中になりほかでは変わらない() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        assertThat(request.markRoutingRequested(1)).as("審査中では変えない").isFalse();
        request.approve(1, submitter, "根拠", now);
        request.markQuotationPresented(1);
        assertThat(request.markRoutingRequested(2)).as("現在の版でなければ変えない").isFalse();
        assertThat(request.markRoutingRequested(1)).isTrue();
        assertThat(request.status()).isEqualTo(TransportRequestStatus.ROUTING);
        assertThat(request.markRoutingRequested(1)).as("2 回目は変えない（冪等）").isFalse();
        assertThat(request.markQuotationPresented(1)).as("遅れて届いた DE-03 では戻らない").isFalse();
    }

    @Test
    void DE03より先にDE16が届いても見積り作成中から経路設計中になる() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);
        request.approve(1, submitter, "根拠", now);

        assertThat(request.markRoutingRequested(1)).isTrue();
        assertThat(request.status()).isEqualTo(TransportRequestStatus.ROUTING);
    }

    @Test
    void 経路設計中の現在の版に経路版が割り当てられると荷主承認待ちになりほかでは変わらない() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        assertThat(request.markAwaitingApproval(1)).as("審査中では変えない").isFalse();
        request.approve(1, submitter, "根拠", now);
        request.markQuotationPresented(1);
        request.markRoutingRequested(1);
        assertThat(request.markAwaitingApproval(2)).as("現在の版でなければ変えない").isFalse();
        assertThat(request.markAwaitingApproval(1)).isTrue();
        assertThat(request.status()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
        assertThat(request.markAwaitingApproval(1)).as("2 回目は変えない（冪等）").isFalse();
        assertThat(request.markRoutingRequested(1)).as("遅れて届いた DE-16 では戻らない").isFalse();
        assertThat(request.markQuotationPresented(1)).as("遅れて届いた DE-03 では戻らない").isFalse();
    }

    @Test
    void 荷主承認待ちの現在の版で荷主が承認すると予約待ちになりほかでは変わらない() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);

        assertThat(request.markReadyToBook(1)).as("審査中では変えない").isFalse();
        request.approve(1, submitter, "根拠", now);
        request.markQuotationPresented(1);
        request.markRoutingRequested(1);
        request.markAwaitingApproval(1);
        assertThat(request.markReadyToBook(2)).as("現在の版でなければ変えない").isFalse();
        assertThat(request.markReadyToBook(1)).isTrue();
        assertThat(request.status()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(request.markReadyToBook(1)).as("2 回目は変えない（冪等）").isFalse();
        assertThat(request.markAwaitingApproval(1)).as("遅れて届いた DE-21 では戻らない").isFalse();
    }

    @Test
    void 先のイベントが遅れても経路設計中から荷主承認待ちと予約待ちに進む() {
        TransportRequest routing = TransportRequest.submit(id, number, shipper, terms, submitter, now);
        routing.approve(1, submitter, "根拠", now);
        routing.markRoutingRequested(1);
        TransportRequest quoted = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()), number, shipper, terms, submitter, now);
        quoted.approve(1, submitter, "根拠", now);
        quoted.markQuotationPresented(1);

        assertThat(routing.markReadyToBook(1)).as("DE-21 より先に DE-04 が届いた").isTrue();
        assertThat(routing.status()).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(quoted.markAwaitingApproval(1)).as("DE-16 より先に DE-21 が届いた").isTrue();
        assertThat(quoted.status()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
    }

    @Test
    void 同じ版でその状態まで進んだかを問い合わせられる() {
        TransportRequest request = TransportRequest.submit(id, number, shipper, terms, submitter, now);
        request.approve(1, submitter, "根拠", now);
        request.markRoutingRequested(1);
        request.markAwaitingApproval(1);

        assertThat(request.hasReached(TransportRequestStatus.QUOTED, 1)).isTrue();
        assertThat(request.hasReached(TransportRequestStatus.AWAITING_APPROVAL, 1))
                .isTrue();
        assertThat(request.hasReached(TransportRequestStatus.READY_TO_BOOK, 1)).isFalse();
        assertThat(request.hasReached(TransportRequestStatus.AWAITING_APPROVAL, 2))
                .as("版が違えば進んでいない")
                .isFalse();
    }
}
