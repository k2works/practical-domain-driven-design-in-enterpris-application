package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransportRequestTest {

    private final TransportRequestId id = new TransportRequestId(UUID.randomUUID());
    private final CompanyId shipper = new CompanyId(UUID.randomUUID());
    private final UserId submitter = new UserId(UUID.randomUUID());
    private final ShipmentTerms terms = new ShipmentTerms(new Location("JPTYO"), new Location("NLRTM"));
    private final UtcInstant now = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));

    @Test
    void 提出すると最初の版が審査中になり提出者と提出時刻が記録される() {
        TransportRequest request = TransportRequest.submit(id, shipper, terms, submitter, now);

        assertThat(request.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(request.currentVersion().versionNo()).isEqualTo(1);
        assertThat(request.currentVersion().terms()).isEqualTo(terms);
        assertThat(request.currentVersion().submittedBy()).isEqualTo(submitter);
        assertThat(request.currentVersion().submittedAt()).isEqualTo(now);
    }

    @Test
    void 提出すると輸送要求を提出したイベントを生成する() {
        TransportRequest request = TransportRequest.submit(id, shipper, terms, submitter, now);

        assertThat(request.domainEvents())
                .containsExactly(new TransportRequestSubmitted(id.value(), 1, shipper, now));
    }

    @Test
    void 生成したイベントは取り出した後に消える() {
        TransportRequest request = TransportRequest.submit(id, shipper, terms, submitter, now);

        request.clearDomainEvents();

        assertThat(request.domainEvents()).isEmpty();
    }
}
