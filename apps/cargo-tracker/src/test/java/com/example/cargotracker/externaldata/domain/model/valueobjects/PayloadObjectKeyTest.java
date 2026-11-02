package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 原本のオブジェクトキーの形（Bolt 39 の行 8b）。 */
class PayloadObjectKeyTest {

    private static final String UUID = "123e4567-e89b-42d3-a456-426614174000";

    @Test
    void 新しいキーは前置きでファイル全体と行のpayloadを区別し毎回違う() {
        assertThat(PayloadObjectKey.newIngestionFile().value()).matches("externaldata/ingestions/[0-9a-f-]{36}");
        assertThat(PayloadObjectKey.newRecordPayload().value()).matches("externaldata/records/[0-9a-f-]{36}");
        assertThat(PayloadObjectKey.newRecordPayload()).isNotEqualTo(PayloadObjectKey.newRecordPayload());
    }

    @Test
    void 形の合う文字列からキーを作れる() {
        assertThat(new PayloadObjectKey("externaldata/ingestions/" + UUID).value())
                .isEqualTo("externaldata/ingestions/" + UUID);
        assertThat(new PayloadObjectKey("externaldata/records/" + UUID).value())
                .isEqualTo("externaldata/records/" + UUID);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "externaldata/records/../../identity/" + UUID,
                "externaldata/records/" + UUID + "/..",
                "/externaldata/records/" + UUID,
                "routing/records/" + UUID,
                "externaldata/other/" + UUID,
                "externaldata/records/not-a-uuid",
                "externaldata/records/" + UUID + "/extra",
                ""
            })
    void 保存先の外を指すキーや前置きの違うキーは作れない(String value) {
        assertThatThrownBy(() -> new PayloadObjectKey(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 空のキーは作れない() {
        assertThatThrownBy(() -> new PayloadObjectKey(null)).isInstanceOf(NullPointerException.class);
    }
}
