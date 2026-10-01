package com.example.cargotracker.identity.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.ならば;
import java.time.Instant;

/**
 * アクセス・監査のステップ定義。アクセス・監査の入力ポートだけを呼ぶ（AT-05）。
 */
public class KpiObservationSteps {

    private final KpiObservationQueryService queryService;
    private final ScenarioContext context;

    public KpiObservationSteps(KpiObservationQueryService queryService, ScenarioContext context) {
        this.queryService = queryService;
        this.context = context;
    }

    @ならば("KPI 計測記録にその輸送要求の提出時刻 {string} が記録される")
    public void KPI計測記録に提出時刻が記録される(String submittedAt) {
        assertThat(queryService.findByTransportRequestId(context.transportRequestId()))
                .hasValueSatisfying(observation ->
                        assertThat(observation.submittedAt()).isEqualTo(new UtcInstant(Instant.parse(submittedAt))));
    }
}
