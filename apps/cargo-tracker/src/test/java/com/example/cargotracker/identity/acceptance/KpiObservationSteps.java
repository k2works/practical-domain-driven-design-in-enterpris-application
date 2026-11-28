package com.example.cargotracker.identity.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.ならば;
import java.time.Duration;
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

    @ならば("KPI 計測記録にその輸送要求の業務番号 {string} が記録される")
    public void KPI計測記録に業務番号が記録される(String transportRequestNumber) {
        assertThat(queryService.findByTransportRequestId(context.transportRequestId()))
                .hasValueSatisfying(observation ->
                        assertThat(observation.transportRequestNumber()).isEqualTo(transportRequestNumber));
    }

    @ならば("KPI 計測記録にその輸送要求の最初の提示時刻 {string} が記録される")
    public void KPI計測記録に最初の提示時刻が記録される(String firstPresentedAt) {
        assertThat(queryService.findByTransportRequestId(context.transportRequestId()))
                .hasValueSatisfying(observation -> assertThat(observation.firstPresentedAt())
                        .hasValue(new UtcInstant(Instant.parse(firstPresentedAt))));
    }

    @ならば("KPI 計測記録のその輸送要求の KPI-01 リードタイムは {int} 時間 {int} 分である")
    public void KPI01リードタイム(int hours, int minutes) {
        assertThat(queryService.findByTransportRequestId(context.transportRequestId()))
                .hasValueSatisfying(observation -> assertThat(observation.leadTime())
                        .hasValue(Duration.ofHours(hours).plusMinutes(minutes)));
    }

    @ならば("KPI 計測記録のその輸送要求は未提示でリードタイムは求まらない")
    public void 未提示でリードタイムは求まらない() {
        assertThat(queryService.findByTransportRequestId(context.transportRequestId()))
                .hasValueSatisfying(observation -> {
                    assertThat(observation.firstPresentedAt()).isEmpty();
                    assertThat(observation.leadTime()).isEmpty();
                });
    }
}
