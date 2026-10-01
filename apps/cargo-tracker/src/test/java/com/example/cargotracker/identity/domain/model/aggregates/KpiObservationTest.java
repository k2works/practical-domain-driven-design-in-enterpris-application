package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KpiObservationTest {

    @Test
    void 輸送要求の提出時刻を記録する() {
        UUID transportRequestId = UUID.randomUUID();
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        UtcInstant submittedAt = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));

        KpiObservation observation = KpiObservation.recordSubmission(transportRequestId, shipper, submittedAt);

        assertThat(observation.transportRequestId()).isEqualTo(transportRequestId);
        assertThat(observation.shipperCompanyId()).isEqualTo(shipper);
        assertThat(observation.submittedAt()).isEqualTo(submittedAt);
    }
}
