package com.example.cargotracker.externaldata.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryDataSourceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 開始判定の照会（Bolt 39b の行 6。ED-INV-01、BR-18、US-14 AC2）。 */
class PilotReadinessQueryServiceTest {

    private static final String NO_APPROVED_SOURCE = "承認済みの情報源がありません。情報源を選定し、承認してください";

    private final InMemoryDataSourceRepository sources = new InMemoryDataSourceRepository();
    private final PilotReadinessQueryService service = new PilotReadinessQueryService(sources);

    private void given(String recordType, ApprovalStatus status) {
        sources.add(new DataSource(UUID.randomUUID(), "提供元-" + UUID.randomUUID(), recordType, status));
    }

    @Test
    void 行6_情報源が0件ならパイロットを開始できずその理由を返す() {
        PilotReadiness readiness = service.readiness();

        assertThat(readiness.approvedCount()).isZero();
        assertThat(readiness.canStart()).isFalse();
        assertThat(readiness.reason()).contains(NO_APPROVED_SOURCE);
    }

    @Test
    void 行6_候補と停止中の情報源だけならパイロットを開始できない() {
        given("VOYAGE", ApprovalStatus.CANDIDATE);
        given("MILESTONE", ApprovalStatus.SUSPENDED);

        PilotReadiness readiness = service.readiness();

        assertThat(readiness.approvedCount()).isZero();
        assertThat(readiness.canStart()).isFalse();
        assertThat(readiness.reason()).contains(NO_APPROVED_SOURCE);
    }

    @Test
    void 行6_承認済みの情報源が1件以上あればパイロットを開始でき承認済みの件数を返す() {
        given("VOYAGE", ApprovalStatus.APPROVED);
        given("MILESTONE", ApprovalStatus.APPROVED);
        given("VOYAGE", ApprovalStatus.SUSPENDED);

        PilotReadiness readiness = service.readiness();

        assertThat(readiness.approvedCount()).isEqualTo(2);
        assertThat(readiness.canStart()).isTrue();
        assertThat(readiness.reason()).isEmpty();
    }
}
