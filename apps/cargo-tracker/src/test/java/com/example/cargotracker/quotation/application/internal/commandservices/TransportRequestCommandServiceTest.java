package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestNumberIssuer;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
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
    private final TransportRequestCommandService service = new TransportRequestCommandService(
            repository, issuer, new MvpAcceptancePolicy(), published::add, Clock.fixed(NOW, ZoneOffset.UTC));

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
