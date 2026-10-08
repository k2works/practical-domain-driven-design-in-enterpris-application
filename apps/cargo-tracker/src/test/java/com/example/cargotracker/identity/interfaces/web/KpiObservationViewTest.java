package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * KPI 計測記録の一覧の 1 行。リードタイムと未提示の経過時間は、どちらか一方だけを示す（Bolt 21）。日時・期間の書式は
 * platform の部品の表のテストで確かめる（Bolt 22）。
 */
class KpiObservationViewTest {

    private static final UtcInstant SUBMITTED_AT = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));

    @Test
    void 提示済みの行はリードタイムを示し経過時間を示さない() {
        KpiObservation observation = KpiObservation.reconstitute(
                UUID.randomUUID(),
                "TR-2026-0001",
                new CompanyId(UUID.randomUUID()),
                SUBMITTED_AT,
                new UtcInstant(Instant.parse("2026-10-06T03:30:00Z")));

        KpiObservationView view = KpiObservationView.from(observation, Instant.parse("2026-10-08T00:00:00Z"));

        assertThat(view.firstPresentedAt()).isEqualTo("2026-10-06 12:30 Asia/Tokyo（UTC+09:00）（UTC 2026-10-06 03:30）");
        assertThat(view.leadTime()).isEqualTo("26 時間 30 分");
        assertThat(view.elapsedSinceSubmission()).isNull();
    }

    @Test
    void 未提示の行は一覧を開いた時刻での経過時間を示しリードタイムを示さない() {
        KpiObservation observation = KpiObservation.recordSubmission(
                UUID.randomUUID(), "TR-2026-0002", new CompanyId(UUID.randomUUID()), SUBMITTED_AT);

        KpiObservationView view = KpiObservationView.from(observation, Instant.parse("2026-10-07T05:10:00Z"));

        assertThat(view.submittedAt()).isEqualTo("2026-10-05 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 01:00）");
        assertThat(view.firstPresentedAt()).isNull();
        assertThat(view.leadTime()).isNull();
        assertThat(view.elapsedSinceSubmission()).isEqualTo("52 時間 10 分");
    }
}
