package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaselineRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** KPI 基準値の保存と最新の取り出し（PostgreSQL。追記専用。W9 の Bolt 44-2）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisKpiBaselineRepositoryIntegrationTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 11, 1);

    @Autowired
    KpiBaselineRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private static UserId registrant() {
        return new UserId(UUID.fromString("00000000-0000-0000-0000-0000044c0099"));
    }

    private KpiBaseline baseline(KpiKind kpi, String p50, Optional<UUID> supersedes, Instant at) {
        return KpiBaseline.register(
                kpi,
                FROM,
                TO,
                new BigDecimal(p50),
                Optional.empty(),
                "現行の記録から算出",
                registrant(),
                new UtcInstant(at),
                supersedes);
    }

    @Test
    void 基準値を保存して最新を読み直すと同じ() {
        KpiBaseline saved = KpiBaseline.register(
                KpiKind.KPI_01,
                FROM,
                TO,
                new BigDecimal("480.00"),
                Optional.of(new BigDecimal("1440.00")),
                "現行の受付台帳から算出",
                registrant(),
                new UtcInstant(Instant.parse("2026-11-16T01:00:00Z")),
                Optional.empty());
        repository.save(saved);

        assertThat(repository.findLatest(KpiKind.KPI_01)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(saved.id());
            assertThat(found.periodFrom()).isEqualTo(FROM);
            assertThat(found.periodTo()).isEqualTo(TO);
            assertThat(found.valueP50()).isEqualByComparingTo("480");
            assertThat(found.valueP90()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1440"));
            assertThat(found.method()).isEqualTo("現行の受付台帳から算出");
            assertThat(found.registeredBy()).isEqualTo(saved.registeredBy());
            assertThat(found.registeredAt()).isEqualTo(saved.registeredAt());
            assertThat(found.supersedes()).isEmpty();
        });
        assertThat(repository.findLatest(KpiKind.KPI_02)).isEmpty();
    }

    @Test
    void 訂正した記録を最新とし_訂正された記録は最新に出ない() {
        KpiBaseline first = baseline(KpiKind.KPI_02, "40.00", Optional.empty(), Instant.parse("2026-11-16T01:00:00Z"));
        repository.save(first);
        KpiBaseline corrected =
                baseline(KpiKind.KPI_02, "45.00", Optional.of(first.id()), Instant.parse("2026-11-16T02:00:00Z"));
        repository.save(corrected);

        assertThat(repository.findLatest(KpiKind.KPI_02))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(corrected.id()));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM identity.kpi_baseline WHERE kpi = 'KPI-02'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void KPI02は90パーセンタイルを持てず_期間が逆の行は表の制約で拒む() {
        UUID id = UUID.randomUUID();
        String insert =
                "INSERT INTO identity.kpi_baseline (id, kpi, period_from, period_to, value_p50, value_p90, method,"
                        + " registered_by, registered_at) VALUES (?, ?, ?, ?, 40, ?, '算出方法', ?, CURRENT_TIMESTAMP)";
        UUID user = registrant().value();

        BigDecimal p90 = new BigDecimal("50");

        assertThatThrownBy(() -> jdbc.update(insert, id, "KPI-02", FROM, TO, p90, user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
