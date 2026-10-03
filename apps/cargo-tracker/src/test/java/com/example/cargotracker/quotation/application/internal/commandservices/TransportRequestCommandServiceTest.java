package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryRequiredDocumentStorage;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestNumberIssuer;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.ResubmissionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransportRequestCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T01:00:00Z");

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final InMemoryTransportRequestNumberIssuer issuer = new InMemoryTransportRequestNumberIssuer();
    private final List<Object> published = new ArrayList<>();
    private final InMemoryRequiredDocumentStorage storage = new InMemoryRequiredDocumentStorage();
    private final TransportRequestCommandService service = new TransportRequestCommandService(
            repository,
            issuer,
            new MvpAcceptancePolicy(),
            new RequiredDocumentPolicy(),
            storage,
            published::add,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static RequiredDocumentAttachment pdf(DocumentType type, String fileName) {
        return new RequiredDocumentAttachment(type, fileName, "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void 書類の違反と輸送条件の不足はまとめて返し書類も業務番号も保存しない() {
        ShipmentTermsInput complete = ShipmentTermsFixture.completeInput();
        ShipmentTermsInput withoutOrigin = new ShipmentTermsInput(
                complete.consigneeCompanyId(),
                null,
                complete.destination(),
                complete.arrivalDeadline(),
                complete.cargoCategory(),
                complete.packageType(),
                complete.packageCount(),
                complete.grossWeightKg(),
                complete.volumeM3());
        RequiredDocumentAttachment text = new RequiredDocumentAttachment(
                DocumentType.COMMERCIAL_INVOICE, "invoice.pdf", "text".getBytes(StandardCharsets.UTF_8));

        SubmissionOutcome outcome = service.submit(new SubmitTransportRequestCommand(
                new CompanyId(UUID.randomUUID()), new UserId(UUID.randomUUID()), withoutOrigin, List.of(text)));

        assertThat(outcome).isInstanceOfSatisfying(SubmissionOutcome.Rejected.class, rejected -> {
            assertThat(rejected.violations().has(Item.ORIGIN, Reason.MISSING)).isTrue();
            assertThat(rejected.violations().has(Item.COMMERCIAL_INVOICE, Reason.UNSUPPORTED_FORMAT))
                    .isTrue();
        });
        assertThat(storage.count()).isZero();
        assertThat(issuer.issuedCount()).isZero();
    }

    @Test
    void 提出した書類は書類番号1から振り大きさとSHA256とオブジェクトキーを残す() {
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) service.submit(new SubmitTransportRequestCommand(
                        new CompanyId(UUID.randomUUID()),
                        new UserId(UUID.randomUUID()),
                        ShipmentTermsFixture.completeInput(),
                        List.of(
                                pdf(DocumentType.COMMERCIAL_INVOICE, "invoice.pdf"),
                                pdf(DocumentType.OTHER, "memo.pdf"))));

        assertThat(repository.findById(submitted.transportRequestId()))
                .hasValueSatisfying(request -> assertThat(
                                request.currentVersion().terms().documents())
                        .satisfiesExactly(
                                first -> {
                                    assertThat(first.documentNo()).isEqualTo(1);
                                    assertThat(first.type()).isEqualTo(DocumentType.COMMERCIAL_INVOICE);
                                    assertThat(first.mediaType()).isEqualTo(DocumentMediaType.PDF);
                                    assertThat(first.sizeBytes()).isEqualTo(13);
                                    assertThat(first.sha256()).hasSize(64);
                                    assertThat(first.objectKey())
                                            .startsWith("quotation/"
                                                    + submitted
                                                            .transportRequestId()
                                                            .value());
                                },
                                second -> assertThat(second.documentNo()).isEqualTo(2)));
        assertThat(storage.count()).isEqualTo(2);
    }

    @Test
    void 下書きでない輸送要求の出し直しは書類を保存する前に拒否する() {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) service.submit(new SubmitTransportRequestCommand(
                        shipper, new UserId(UUID.randomUUID()), ShipmentTermsFixture.completeInput()));

        ResubmissionOutcome outcome = service.resubmit(new ResubmitTransportRequestCommand(
                submitted.number(),
                shipper,
                new UserId(UUID.randomUUID()),
                ShipmentTermsFixture.completeInput(),
                List.of(pdf(DocumentType.PACKING_LIST, "packing.pdf"))));

        assertThat(outcome).isEqualTo(new ResubmissionOutcome.Rejected(ResubmissionRejection.NOT_DRAFT));
        assertThat(storage.count()).isZero();
    }

    private final SubmitTransportRequestCommand command = new SubmitTransportRequestCommand(
            new CompanyId(UUID.randomUUID()), new UserId(UUID.randomUUID()), ShipmentTermsFixture.completeInput());

    @Test
    void 提出した輸送要求にClockの年の業務番号を振りClockの時刻で保存する() {
        SubmissionOutcome.Submitted submitted = (SubmissionOutcome.Submitted) service.submit(command);

        assertThat(submitted.number()).isEqualTo(new TransportRequestNumber(2026, 1));
        assertThat(repository.findById(submitted.transportRequestId())).hasValueSatisfying(request -> {
            assertThat(request.number()).isEqualTo(submitted.number());
            assertThat(request.currentVersion().submittedAt()).isEqualTo(new UtcInstant(NOW));
            assertThat(request.currentVersion().terms()).isEqualTo(ShipmentTermsFixture.generalCargo());
        });
    }

    @Test
    void 保存した輸送要求のDE01を1回だけ発行しイベントを残さない() {
        SubmissionOutcome.Submitted submitted = (SubmissionOutcome.Submitted) service.submit(command);

        assertThat(published)
                .containsExactly(new TransportRequestSubmitted(
                        submitted.transportRequestId().value(),
                        1,
                        command.shipperCompanyId(),
                        new UtcInstant(NOW),
                        "TR-2026-0001"));
        assertThat(repository.findById(submitted.transportRequestId()))
                .hasValueSatisfying(
                        request -> assertThat(request.domainEvents()).isEmpty());
    }

    @Test
    void 違反があれば保存も採番もイベントの発行もせずに違反を返す() {
        SubmitTransportRequestCommand incomplete = new SubmitTransportRequestCommand(
                command.shipperCompanyId(),
                command.submittedBy(),
                new ShipmentTermsInput(null, null, null, null, null, null, null, null, null));

        SubmissionOutcome outcome = service.submit(incomplete);

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        SubmissionOutcome.Rejected.class,
                        rejected -> assertThat(rejected.violations().has(Item.CONSIGNEE, Reason.MISSING))
                                .isTrue());
        assertThat(repository.count()).isZero();
        assertThat(issuer.issuedCount()).isZero();
        assertThat(published).isEmpty();
    }
}
