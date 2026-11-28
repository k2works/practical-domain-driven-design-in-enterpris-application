package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 判断を待つ隔離・差異の行の不変条件（Bolt 39h2 の行 6）。 */
class UndecidedRecordTest {

    private static UndecidedRecord recordOf(Classification classification, String externalId, String externalVersion) {
        return new UndecidedRecord(
                UUID.randomUUID(),
                classification,
                UUID.randomUUID(),
                Instant.parse("2026-11-16T01:05:00Z"),
                UUID.randomUUID(),
                new PhysicalLineRange(4, 4),
                externalId,
                externalVersion,
                "注記");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Classification.class,
            names = {"QUARANTINED", "DISCREPANCY"},
            mode = EnumSource.Mode.EXCLUDE)
    void 行6_a_分類が隔離か差異でなければ作れない(Classification classification) {
        assertThatThrownBy(() -> recordOf(classification, "MS-0001", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(classification.name());
    }

    @Test
    void 行6_b_差異は外部原本を持つので外部IDと外部版がなければ作れない() {
        assertThatThrownBy(() -> recordOf(Classification.DISCREPANCY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(recordOf(Classification.DISCREPANCY, "MS-0001", "1").hasExternalRecord())
                .isTrue();
    }

    @Test
    void 行6_c_キーのない隔離は外部IDと外部版なしで作れる() {
        UndecidedRecord keyless = recordOf(Classification.QUARANTINED, null, null);

        assertThat(keyless.hasExternalRecord()).isFalse();
        assertThat(keyless.classification()).isEqualTo(Classification.QUARANTINED);
    }

    @Test
    void 行6_d_外部IDと外部版はそろって持つかそろって持たない() {
        assertThatThrownBy(() -> recordOf(Classification.QUARANTINED, "MS-0001", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recordOf(Classification.QUARANTINED, null, "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
