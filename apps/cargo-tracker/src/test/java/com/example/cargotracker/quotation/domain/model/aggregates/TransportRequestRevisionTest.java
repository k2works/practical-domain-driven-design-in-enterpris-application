package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.valueobjects.RevisionRejection;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 本予約前の条件の変更（US-05 AC1。Bolt 62。CP-62-1）。旧版を残した新しい版を作り、承認を要さず審査中にする。
 */
class TransportRequestRevisionTest {

    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final UtcInstant SUBMITTED_AT = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));
    private static final UtcInstant REVISED_AT = new UtcInstant(Instant.parse("2026-10-07T01:00:00Z"));
    private static final ShipmentTerms TERMS = ShipmentTermsFixture.generalCargo();

    private final TransportRequestId id = new TransportRequestId(UUID.randomUUID());
    private final TransportRequestNumber number = new TransportRequestNumber(2026, 1);
    private final CompanyId shipper = new CompanyId(UUID.randomUUID());

    @ParameterizedTest
    @EnumSource(
            value = TransportRequestStatus.class,
            names = {"UNDER_REVIEW", "QUOTING", "QUOTED", "ROUTING", "AWAITING_APPROVAL", "READY_TO_BOOK"})
    void 本予約の前は変更すると版を1増やして審査中になりDE01を出す(TransportRequestStatus status) {
        TransportRequest request = in(status);

        assertThat(request.revise(1, TERMS, SHIPPER_USER, REVISED_AT)).isEmpty();

        assertThat(request.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(request.currentVersion().versionNo()).isEqualTo(2);
        assertThat(request.newVersion()).hasValue(request.currentVersion());
        assertThat(request.number()).isEqualTo(number);
        assertThat(request.domainEvents())
                .containsExactly(new TransportRequestSubmitted(id.value(), 2, shipper, REVISED_AT, number.text()));
    }

    @ParameterizedTest
    @EnumSource(
            value = TransportRequestStatus.class,
            names = {"DRAFT", "WITHDRAWN", "BOOKED"})
    void 下書きと辞退で終了と予約確定済みは変更できず何も変わらない(TransportRequestStatus status) {
        TransportRequest request = in(status);

        assertThat(request.revise(1, TERMS, SHIPPER_USER, REVISED_AT)).hasValue(RevisionRejection.NOT_REVISABLE);

        assertThat(request.status()).isEqualTo(status);
        assertThat(request.currentVersion().versionNo()).isEqualTo(1);
        assertThat(request.newVersion()).isEmpty();
        assertThat(request.domainEvents()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = TransportRequestStatus.class,
            names = {"QUOTED", "READY_TO_BOOK"})
    void 見ていた版が現在の版でなければ上書きしない(TransportRequestStatus status) {
        TransportRequest request = in(status);

        assertThat(request.revise(2, TERMS, SHIPPER_USER, REVISED_AT)).hasValue(RevisionRejection.STALE_VERSION);

        assertThat(request.status()).isEqualTo(status);
        assertThat(request.domainEvents()).isEmpty();
    }

    private TransportRequest in(TransportRequestStatus status) {
        TransportRequest submitted = TransportRequest.submit(id, number, shipper, TERMS, SHIPPER_USER, SUBMITTED_AT);
        return TransportRequest.reconstitute(id, number, shipper, status, submitted.currentVersion(), List.of(), 0L);
    }
}
