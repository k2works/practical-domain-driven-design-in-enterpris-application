package com.example.cargotracker.identity.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.TrackingInquiryStatistics;
import com.example.cargotracker.identity.domain.model.aggregates.IncompleteEventPublicationCounter;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaselineRepository;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.interfaces.api.InquiryCounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 基準値とパイロット中の値を並べる照会と、未完了の発行の件数（US-21 AC5。W9 の Bolt 44-2。台帳 D-172。仮）。 */
class KpiComparisonQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

    private static KpiBaseline baseline(KpiKind kpi, String p50) {
        return KpiBaseline.register(
                kpi,
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 11, 1),
                new BigDecimal(p50),
                Optional.empty(),
                "現行の記録から算出",
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-11-16T01:00:00Z")),
                Optional.empty());
    }

    private KpiComparisonQueryService service(
            Optional<KpiBaseline> kpi01, Optional<KpiBaseline> kpi02, long incomplete) {
        KpiObservationRepository observations = new KpiObservationRepository() {
            @Override
            public void save(KpiObservation observation) {
                // このテストでは使わない
            }

            @Override
            public void saveFirstPresentation(KpiObservation observation) {
                // このテストでは使わない
            }

            @Override
            public Optional<KpiObservation> findByTransportRequestId(UUID transportRequestId) {
                return Optional.empty();
            }

            @Override
            public List<KpiObservation> findAll() {
                return List.of();
            }
        };
        KpiBaselineRepository baselines = new KpiBaselineRepository() {
            @Override
            public void save(KpiBaseline baseline) {
                // このテストでは使わない
            }

            @Override
            public Optional<KpiBaseline> findLatest(KpiKind kpi) {
                return kpi == KpiKind.KPI_01 ? kpi01 : kpi02;
            }
        };
        IncompleteEventPublicationCounter counter = () -> incomplete;
        return new KpiComparisonQueryService(
                new KpiWeeklyQueryService(
                        observations, new TrackingInquiryStatistics((from, to) -> new InquiryCounts(10, 4))),
                baselines,
                counter,
                id -> "監査 花子");
    }

    @Test
    void 基準値とパイロット中の値と未完了の発行の件数を並べて返す() {
        KpiBaseline kpi01 = baseline(KpiKind.KPI_01, "480");
        KpiBaseline kpi02 = baseline(KpiKind.KPI_02, "30");

        KpiComparison comparison =
                service(Optional.of(kpi01), Optional.of(kpi02), 2).compare(NOW, 12);

        assertThat(comparison.kpi01()).contains(kpi01);
        assertThat(comparison.kpi01Registrant()).contains("監査 花子");
        assertThat(comparison.kpi02()).contains(kpi02);
        assertThat(comparison.kpi02Registrant()).contains("監査 花子");
        assertThat(comparison.pilot().inquiryTotal()).isEqualTo(10);
        assertThat(comparison.pilot().selfServiceRate()).contains(40.0);
        assertThat(comparison.incompletePublications()).isEqualTo(2);
    }

    @Test
    void 基準値が未登録なら空で返す() {
        KpiComparison comparison =
                service(Optional.empty(), Optional.empty(), 0).compare(NOW, 12);

        assertThat(comparison.kpi01()).isEmpty();
        assertThat(comparison.kpi02()).isEmpty();
        assertThat(comparison.kpi01Registrant()).isEmpty();
        assertThat(comparison.incompletePublications()).isZero();
    }
}
