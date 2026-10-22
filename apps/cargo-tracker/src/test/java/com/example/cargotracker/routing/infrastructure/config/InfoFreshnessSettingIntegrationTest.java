package com.example.cargotracker.routing.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.UserId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 情報鮮度の判定の許容時間が設定 {@code cargotracker.routing.info-freshness-tolerance} から入力ポートまで届く（D-89。Bolt 37b2）。
 * 開発データの見本の案件（RC-2026-0902。適合 2 件）の航海を 25 時間前の取得にして、入力ポートで算出し直す。
 * 取得時刻は判定と同じ JVM の時計から作る（T-171）。トランザクションは戻す。
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class InfoFreshnessSettingIntegrationTest {

    static final RoutingCaseNumber SAMPLE = new RoutingCaseNumber(2026, 902);
    static final UserId ROUTE_DESIGNER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000302"));

    @Autowired
    RoutingCaseCommandService commandService;

    @Autowired
    RoutingCaseRepository routingCases;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void 見本の航海を25時間前の取得にする() {
        jdbc.update(
                "UPDATE routing.voyage SET acquired_at = ? WHERE voyage_number LIKE 'DEMO-%'",
                Timestamp.from(Instant.now().minus(Duration.ofHours(25))));
    }

    @Test
    void 既定の24時間では25時間前に取得した航海を使う候補はすべて情報不足で除外する() {
        CandidateCalculation calculation = recalculate();

        assertThat(calculation.found()).isEqualTo(5);
        assertThat(calculation.conforming()).isZero();
    }

    @Nested
    @TestPropertySource(properties = "cargotracker.routing.info-freshness-tolerance=PT48H")
    class 許容時間を48時間にすると {

        @Test
        void 二十五時間前に取得した航海を使う候補も適合する() {
            CandidateCalculation calculation = recalculate();

            assertThat(calculation.found()).isEqualTo(5);
            assertThat(calculation.conforming()).isEqualTo(2);
        }
    }

    CandidateCalculation recalculate() {
        long version = routingCases.findByNumber(SAMPLE).orElseThrow().aggregateVersion();
        CandidateCalculationOutcome outcome =
                commandService.calculateCandidates(new CalculateCandidatesCommand(SAMPLE, version, ROUTE_DESIGNER));
        assertThat(outcome).isInstanceOf(CandidateCalculationOutcome.Calculated.class);
        return ((CandidateCalculationOutcome.Calculated) outcome).calculation();
    }
}
