package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 接続時間規則の適用期間は開始を含み終わりを含まない（Bolt 17 レビュー D-63。T-38 の 3 点）。 */
class ConnectionRuleTest {

    static final Location SINGAPORE = new Location("SGSIN");
    static final ConnectionRule RULE = new ConnectionRule(
            UUID.randomUUID(),
            SINGAPORE,
            Duration.ofHours(8),
            new UtcInstant(Instant.parse("2026-10-01T00:00:00Z")),
            new UtcInstant(Instant.parse("2026-11-01T00:00:00Z")));

    @ParameterizedTest(name = "{0} に適用するか={1}")
    @CsvSource({
        "2026-09-30T23:59:59Z, false",
        "2026-10-01T00:00:00Z, true",
        "2026-10-01T00:00:01Z, true",
        "2026-10-31T23:59:59Z, true",
        "2026-11-01T00:00:00Z, false"
    })
    void 適用期間は開始を含み終わりを含まない(String judgedAt, boolean applies) {
        assertThat(RULE.appliesTo(SINGAPORE, new UtcInstant(Instant.parse(judgedAt))))
                .isEqualTo(applies);
    }

    @ParameterizedTest(name = "{0} の規則は SGSIN に適用しない")
    @CsvSource({"HKHKG"})
    void ほかの港には適用しない(String port) {
        assertThat(RULE.appliesTo(new Location(port), new UtcInstant(Instant.parse("2026-10-15T00:00:00Z"))))
                .isFalse();
    }
}
