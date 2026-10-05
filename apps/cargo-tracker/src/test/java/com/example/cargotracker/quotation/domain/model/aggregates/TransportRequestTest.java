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
}
