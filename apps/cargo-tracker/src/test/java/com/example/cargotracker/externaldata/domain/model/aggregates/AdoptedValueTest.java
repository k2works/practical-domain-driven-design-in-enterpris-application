package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 採用値（Bolt 39b3 の行 1・11）。値の要約は 4000 コードポイントまで（{@code value_summary VARCHAR(4000)}）。 */
class AdoptedValueTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-03T09:00:00.123456Z");

    private static AdoptedValue adoptedValue(String valueSummary) {
        return new AdoptedValue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TargetType.MILESTONE,
                "CTABCDEFGH2345",
                valueSummary,
                "1",
                PUBLISHED_AT);
    }

    @Test
    void 行1_採用値は受信記録と対象の種類とキーと要約と情報版と公開した時刻を持つ() {
        AdoptedValue value = adoptedValue("DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z");

        assertThat(value.targetType()).isEqualTo(TargetType.MILESTONE);
        assertThat(value.targetKey()).isEqualTo("CTABCDEFGH2345");
        assertThat(value.valueSummary()).isEqualTo("DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z");
        assertThat(value.infoVersion()).isEqualTo("1");
        assertThat(value.publishedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void 行11_要約は4000コードポイントまで受け4001は拒む() {
        String fourThousandWithSupplementary = "a".repeat(3999) + "𠀋";

        assertThat(adoptedValue("a".repeat(4000)).valueSummary()).hasSize(4000);
        assertThat(adoptedValue(fourThousandWithSupplementary).valueSummary()).isEqualTo(fourThousandWithSupplementary);
        String tooLong = "a".repeat(4001);
        assertThatThrownBy(() -> adoptedValue(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行1_採用値はどの値のNULLも拒む() {
        UUID id = UUID.randomUUID();
        UUID receivedRecordId = UUID.randomUUID();

        assertThatThrownBy(
                        () -> new AdoptedValue(null, receivedRecordId, TargetType.VOYAGE, "V1", "s", "1", PUBLISHED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdoptedValue(id, null, TargetType.VOYAGE, "V1", "s", "1", PUBLISHED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdoptedValue(id, receivedRecordId, null, "V1", "s", "1", PUBLISHED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new AdoptedValue(id, receivedRecordId, TargetType.VOYAGE, null, "s", "1", PUBLISHED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new AdoptedValue(id, receivedRecordId, TargetType.VOYAGE, "V1", null, "1", PUBLISHED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new AdoptedValue(id, receivedRecordId, TargetType.VOYAGE, "V1", "s", null, PUBLISHED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdoptedValue(id, receivedRecordId, TargetType.VOYAGE, "V1", "s", "1", null))
                .isInstanceOf(NullPointerException.class);
    }
}
