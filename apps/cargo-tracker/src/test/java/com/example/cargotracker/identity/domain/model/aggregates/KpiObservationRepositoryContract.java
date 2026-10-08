package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * KPI 計測記録のリポジトリの契約。メモリ上の実装と MyBatis の実装の両方で同じ振る舞いを確かめる。
 */
public abstract class KpiObservationRepositoryContract {

    protected abstract KpiObservationRepository repository();

    private static KpiObservation submittedAt(UUID transportRequestId, String instant) {
        return KpiObservation.recordSubmission(
                transportRequestId,
                "TR-2026-0001",
                new CompanyId(UUID.randomUUID()),
                new UtcInstant(Instant.parse(instant)));
    }

    @Test
    void 保存した記録を輸送要求IDで読み出せる() {
        UUID transportRequestId = UUID.randomUUID();
        KpiObservation observation = submittedAt(transportRequestId, "2026-10-05T01:00:00Z");

        repository().save(observation);

        assertThat(repository().findByTransportRequestId(transportRequestId)).hasValueSatisfying(found -> {
            assertThat(found.transportRequestNumber()).isEqualTo(observation.transportRequestNumber());
            assertThat(found.shipperCompanyId()).isEqualTo(observation.shipperCompanyId());
            assertThat(found.submittedAt()).isEqualTo(observation.submittedAt());
        });
    }

    @Test
    void 業務番号のない古い記録も保存して読み出せる() {
        UUID transportRequestId = UUID.randomUUID();

        repository()
                .save(KpiObservation.recordSubmission(
                        transportRequestId,
                        null,
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"))));

        assertThat(repository().findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(
                        found -> assertThat(found.transportRequestNumber()).isNull());
    }

    @Test
    void 記録のない輸送要求は見つからない() {
        assertThat(repository().findByTransportRequestId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void 同じ輸送要求の記録を2回保存しても最初の記録が残る() {
        UUID transportRequestId = UUID.randomUUID();
        KpiObservation first = submittedAt(transportRequestId, "2026-10-05T01:00:00Z");

        repository().save(first);
        repository().save(submittedAt(transportRequestId, "2026-10-06T01:00:00Z"));

        assertThat(repository().findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(found -> assertThat(found.submittedAt()).isEqualTo(first.submittedAt()));
        assertThat(repository().findAll())
                .filteredOn(o -> o.transportRequestId().equals(transportRequestId))
                .hasSize(1);
    }

    @Test
    void 一覧は提出時刻の新しい順に並ぶ() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        repository().save(submittedAt(older, "2026-10-05T01:00:00Z"));
        repository().save(submittedAt(newer, "2026-10-05T02:00:00Z"));

        assertThat(repository().findAll())
                .extracting(KpiObservation::transportRequestId)
                .filteredOn(id -> id.equals(older) || id.equals(newer))
                .containsExactly(newer, older);
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    /** 保存した記録を読み出し、提示を記録して最初の提示時刻を保存する（listener と同じ流れ）。 */
    private void presentAt(UUID transportRequestId, String presentedAt) {
        KpiObservation found =
                repository().findByTransportRequestId(transportRequestId).orElseThrow();
        found.recordPresentation(at(presentedAt));
        repository().saveFirstPresentation(found);
    }

    @Test
    void 最初の提示時刻を保存して読み出せる() {
        UUID transportRequestId = UUID.randomUUID();
        repository().save(submittedAt(transportRequestId, "2026-10-05T01:00:00Z"));

        presentAt(transportRequestId, "2026-10-05T04:30:00Z");

        assertThat(repository().findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(
                        found -> assertThat(found.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z")));
    }

    @Test
    void 提示していない記録の最初の提示時刻は空のまま読み出せる() {
        UUID transportRequestId = UUID.randomUUID();
        repository().save(submittedAt(transportRequestId, "2026-10-05T01:00:00Z"));

        assertThat(repository().findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(
                        found -> assertThat(found.firstPresentedAt()).isEmpty());
    }

    @Test
    void 保存されている時刻より遅い提示時刻を保存しても変わらない() {
        UUID transportRequestId = UUID.randomUUID();
        repository().save(submittedAt(transportRequestId, "2026-10-05T01:00:00Z"));
        presentAt(transportRequestId, "2026-10-05T04:30:00Z");
        KpiObservation stale = submittedAt(transportRequestId, "2026-10-05T01:00:00Z");
        stale.recordPresentation(at("2026-10-06T01:00:00Z"));

        repository().saveFirstPresentation(stale);

        assertThat(repository().findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(
                        found -> assertThat(found.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z")));
    }

    @Test
    void 保存されている時刻より早い提示時刻を保存すると書き換わる() {
        UUID transportRequestId = UUID.randomUUID();
        repository().save(submittedAt(transportRequestId, "2026-10-05T01:00:00Z"));
        presentAt(transportRequestId, "2026-10-06T01:00:00Z");
        KpiObservation stale = submittedAt(transportRequestId, "2026-10-05T01:00:00Z");
        stale.recordPresentation(at("2026-10-05T04:30:00Z"));

        repository().saveFirstPresentation(stale);

        assertThat(repository().findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(
                        found -> assertThat(found.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z")));
    }

    @Test
    void 記録のない輸送要求の提示時刻を保存しても記録はできない() {
        UUID transportRequestId = UUID.randomUUID();
        KpiObservation notSaved = submittedAt(transportRequestId, "2026-10-05T01:00:00Z");
        notSaved.recordPresentation(at("2026-10-05T04:30:00Z"));

        repository().saveFirstPresentation(notSaved);

        assertThat(repository().findByTransportRequestId(transportRequestId)).isEmpty();
    }
}
