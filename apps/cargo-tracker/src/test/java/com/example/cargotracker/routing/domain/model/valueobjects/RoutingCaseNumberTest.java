package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoutingCaseNumberTest {

    @Test
    void 表記はRC_年_4桁の連番() {
        assertThat(new RoutingCaseNumber(2026, 88).text()).isEqualTo("RC-2026-0088");
        assertThat(new RoutingCaseNumber(2026, 12345).text()).isEqualTo("RC-2026-12345");
    }

    @Test
    void 表記から読み戻せる() {
        assertThat(RoutingCaseNumber.parse("RC-2026-0088")).isEqualTo(new RoutingCaseNumber(2026, 88));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TR-2026-0001", "RC-2026-001", "RC-2026-00001", "rc-2026-0001", ""})
    void 表記の規則と違う文字列は読まない(String text) {
        assertThatThrownBy(() -> RoutingCaseNumber.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 年は依頼時刻の日本時間の年() {
        assertThat(RoutingCaseNumber.yearOf(new UtcInstant(Instant.parse("2026-12-31T14:59:59Z"))))
                .isEqualTo(2026);
        assertThat(RoutingCaseNumber.yearOf(new UtcInstant(Instant.parse("2026-12-31T15:00:00Z"))))
                .isEqualTo(2027);
    }
}
