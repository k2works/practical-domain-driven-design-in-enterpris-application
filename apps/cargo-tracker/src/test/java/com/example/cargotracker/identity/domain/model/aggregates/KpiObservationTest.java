package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KpiObservationTest {

    @Test
    void 輸送要求の業務番号と提出時刻を記録する() {
        UUID transportRequestId = UUID.randomUUID();
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        UtcInstant submittedAt = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));

        KpiObservation observation =
                KpiObservation.recordSubmission(transportRequestId, "TR-2026-0001", shipper, submittedAt);

        assertThat(observation.transportRequestId()).isEqualTo(transportRequestId);
        assertThat(observation.transportRequestNumber()).isEqualTo("TR-2026-0001");
        assertThat(observation.shipperCompanyId()).isEqualTo(shipper);
        assertThat(observation.submittedAt()).isEqualTo(submittedAt);
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private static KpiObservation submittedAt(String instant) {
        return KpiObservation.recordSubmission(
                UUID.randomUUID(), "TR-2026-0001", new CompanyId(UUID.randomUUID()), at(instant));
    }

    @Test
    void 提示する前は最初の提示時刻もリードタイムもない() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");

        assertThat(observation.firstPresentedAt()).isEmpty();
        assertThat(observation.leadTime()).isEmpty();
    }

    @Test
    void 未提示の記録に提示を記録すると最初の提示時刻になりリードタイムが求まる() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");

        boolean changed = observation.recordPresentation(at("2026-10-06T03:30:00Z"));

        assertThat(changed).isTrue();
        assertThat(observation.firstPresentedAt()).hasValue(at("2026-10-06T03:30:00Z"));
        assertThat(observation.leadTime()).hasValue(Duration.ofHours(26).plusMinutes(30));
    }

    @Test
    void 提出の1分後の提示ならリードタイムは1分() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");

        observation.recordPresentation(at("2026-10-05T01:01:00Z"));

        assertThat(observation.leadTime()).hasValue(Duration.ofMinutes(1));
    }

    @Test
    void 提出時刻と同時刻の提示は受け付けリードタイムは0() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");

        assertThat(observation.recordPresentation(at("2026-10-05T01:00:00Z"))).isTrue();
        assertThat(observation.leadTime()).hasValue(Duration.ZERO);
    }

    @Test
    void 提出時刻より前の提示は拒否する() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");

        assertThatThrownBy(() -> observation.recordPresentation(at("2026-10-05T00:59:59Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(observation.firstPresentedAt()).isEmpty();
    }

    @Test
    void 再見積りなどで後から遅い提示が届いても最初の提示時刻は変わらない() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");
        observation.recordPresentation(at("2026-10-05T04:30:00Z"));

        boolean changed = observation.recordPresentation(at("2026-10-06T01:00:00Z"));

        assertThat(changed).isFalse();
        assertThat(observation.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z"));
    }

    @Test
    void 届く順が入れ替わり早い提示が後から届いたら最初の提示時刻を書き換える() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");
        observation.recordPresentation(at("2026-10-06T01:00:00Z"));

        boolean changed = observation.recordPresentation(at("2026-10-05T04:30:00Z"));

        assertThat(changed).isTrue();
        assertThat(observation.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z"));
    }

    @Test
    void 同じ提示がもう一度届いても何も変えない() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");
        observation.recordPresentation(at("2026-10-05T04:30:00Z"));

        assertThat(observation.recordPresentation(at("2026-10-05T04:30:00Z"))).isFalse();
        assertThat(observation.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z"));
    }

    @Test
    void 提出時刻より前の提示時刻かを問い合わせられる() {
        KpiObservation observation = submittedAt("2026-10-05T01:00:00Z");

        assertThat(observation.precedesSubmission(at("2026-10-05T00:59:59Z"))).isTrue();
        assertThat(observation.precedesSubmission(at("2026-10-05T01:00:00Z"))).isFalse();
    }
}
