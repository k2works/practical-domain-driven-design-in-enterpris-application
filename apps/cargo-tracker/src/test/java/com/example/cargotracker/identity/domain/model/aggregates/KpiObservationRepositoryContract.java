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
                transportRequestId, new CompanyId(UUID.randomUUID()), new UtcInstant(Instant.parse(instant)));
    }

    @Test
    void 保存した記録を輸送要求IDで読み出せる() {
        UUID transportRequestId = UUID.randomUUID();
        KpiObservation observation = submittedAt(transportRequestId, "2026-10-05T01:00:00Z");

        repository().save(observation);

        assertThat(repository().findByTransportRequestId(transportRequestId)).hasValueSatisfying(found -> {
            assertThat(found.shipperCompanyId()).isEqualTo(observation.shipperCompanyId());
            assertThat(found.submittedAt()).isEqualTo(observation.submittedAt());
        });
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
}
