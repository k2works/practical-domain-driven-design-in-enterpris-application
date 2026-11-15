package com.example.cargotracker.identity.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.TrackingInquiryStatistics;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.interfaces.api.InquiryCounts;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 週次の KPI の照会（US-21 AC4。W9 の Bolt 44-1。台帳 D-171。仮）。週は UTC の月曜始まり。 */
class KpiWeeklyQueryServiceTest {

    /** 現在は 2026-11-18（水）。今週は 2026-11-16 から。 */
    private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000044b0001"));

    private final List<KpiObservation> observations = new ArrayList<>();
    private final List<Instant[]> askedRanges = new ArrayList<>();

    private final KpiObservationRepository repository = new KpiObservationRepository() {
        @Override
        public void save(KpiObservation observation) {
            observations.add(observation);
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
            return observations;
        }
    };

    /** 週ごとに、(総数, Web で完結) を返す代役。範囲を覚える。 */
    private KpiWeeklyQueryService service(long total, long selfServed) {
        return new KpiWeeklyQueryService(repository, new TrackingInquiryStatistics((from, to) -> {
            askedRanges.add(new Instant[] {from, to});
            return from.equals(Instant.parse("2026-11-16T00:00:00Z"))
                    ? new InquiryCounts(total, selfServed)
                    : new InquiryCounts(0, 0);
        }));
    }

    private void observed(String submittedAt, String presentedAt) {
        KpiObservation observation = KpiObservation.recordSubmission(
                UUID.randomUUID(),
                "TR-2026-" + (observations.size() + 1),
                SHIPPER,
                new UtcInstant(Instant.parse(submittedAt)));
        if (presentedAt != null) {
            observation.recordPresentation(new UtcInstant(Instant.parse(presentedAt)));
        }
        observations.add(observation);
    }

    @Test
    void 週は新しい週から並び_今週を含む指定の週数を返す() {
        List<KpiWeeklyRow> rows = service(0, 0).recentWeeks(NOW, 3);

        assertThat(rows)
                .extracting(KpiWeeklyRow::weekStart)
                .containsExactly(
                        Instant.parse("2026-11-16T00:00:00Z"),
                        Instant.parse("2026-11-09T00:00:00Z"),
                        Instant.parse("2026-11-02T00:00:00Z"));
    }

    @Test
    void KPI01は週内に最初の提示をした見積依頼のリードタイムの中央値とP90を示す() {
        // 今週（11/16〜）に提示した 3 件: 2 時間・4 時間・10 時間。提出は先週でもよい
        observed("2026-11-16T01:00:00Z", "2026-11-16T03:00:00Z");
        observed("2026-11-13T22:00:00Z", "2026-11-17T02:00:00Z");
        observed("2026-11-17T00:00:00Z", "2026-11-17T10:00:00Z");
        // 先週に提示した 1 件と、まだ提示していない 1 件（算出しない）
        observed("2026-11-09T00:00:00Z", "2026-11-09T05:00:00Z");
        observed("2026-11-17T00:00:00Z", null);

        List<KpiWeeklyRow> rows = service(0, 0).recentWeeks(NOW, 2);

        KpiWeeklyRow thisWeek = rows.get(0);
        assertThat(thisWeek.leadTimeCount()).isEqualTo(3);
        // 2 時間・約 76 時間・10 時間 → 昇順 2 時間・10 時間・76 時間。中央値 10 時間、P90 は ceil(2.7) = 3 番目
        assertThat(thisWeek.median()).contains(Duration.ofHours(10));
        assertThat(thisWeek.percentile90()).contains(Duration.ofHours(76));
        KpiWeeklyRow lastWeek = rows.get(1);
        assertThat(lastWeek.leadTimeCount()).isEqualTo(1);
        assertThat(lastWeek.median()).contains(Duration.ofHours(5));
    }

    @Test
    void 提示のない週はKPI01の値を持たない() {
        List<KpiWeeklyRow> rows = service(0, 0).recentWeeks(NOW, 1);

        assertThat(rows.getFirst().leadTimeCount()).isZero();
        assertThat(rows.getFirst().median()).isEmpty();
        assertThat(rows.getFirst().percentile90()).isEmpty();
    }

    @Test
    void KPI02は週の照会総数に対するWebで完結した割合を示し_総数が0なら値を持たない() {
        List<KpiWeeklyRow> rows = service(8, 6).recentWeeks(NOW, 2);

        assertThat(rows.get(0).inquiryTotal()).isEqualTo(8);
        assertThat(rows.get(0).inquirySelfServed()).isEqualTo(6);
        assertThat(rows.get(0).selfServiceRate()).contains(75.0);
        assertThat(rows.get(1).inquiryTotal()).isZero();
        assertThat(rows.get(1).selfServiceRate()).isEmpty();
    }

    @Test
    void パイロットの値は直近の週数ぶんのリードタイムと照会をまとめて求める() {
        // 今週（11/16〜）と先週（11/09〜）に 1 件ずつ提示。2 週ぶんを見る
        observed("2026-11-16T01:00:00Z", "2026-11-16T05:00:00Z");
        observed("2026-11-09T01:00:00Z", "2026-11-09T03:00:00Z");
        // 範囲の外（3 週前）は含まない
        observed("2026-11-01T01:00:00Z", "2026-11-02T01:00:00Z");
        KpiWeeklyQueryService service =
                new KpiWeeklyQueryService(repository, new TrackingInquiryStatistics((from, to) -> {
                    askedRanges.add(new Instant[] {from, to});
                    return new InquiryCounts(10, 4);
                }));

        KpiPilotSummary summary = service.pilotSummary(NOW, 2);

        assertThat(summary.leadTimeCount()).isEqualTo(2);
        // 2 時間と 4 時間 → 中央値は 3 時間、P90 は ceil(1.8) = 2 番目の 4 時間
        assertThat(summary.median()).contains(Duration.ofHours(3));
        assertThat(summary.percentile90()).contains(Duration.ofHours(4));
        assertThat(summary.inquiryTotal()).isEqualTo(10);
        assertThat(summary.inquirySelfServed()).isEqualTo(4);
        assertThat(summary.selfServiceRate()).contains(40.0);
        assertThat(askedRanges).singleElement().satisfies(range -> {
            assertThat(range[0]).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
            assertThat(range[1]).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
        });
    }

    @Test
    void 追跡へは週の始まり以後_次の週の始まりより前の範囲で問い合わせる() {
        service(0, 0).recentWeeks(NOW, 1);

        assertThat(askedRanges).singleElement().satisfies(range -> {
            assertThat(range[0]).isEqualTo(Instant.parse("2026-11-16T00:00:00Z"));
            assertThat(range[1]).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
        });
    }
}
