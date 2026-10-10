package com.example.cargotracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 出典の不変条件（種類・参照・取得時刻を必須とする。参照は 1〜200 文字。Bolt 26b）。 */
class SourceTest {

    private static final UtcInstant ACQUIRED_AT = new UtcInstant(Instant.parse("2026-11-01T03:00:00Z"));

    @Test
    void 種類と参照と取得時刻で出典を作る() {
        Source source = new Source(SourceKind.FIELD_RECORD, "F-118", ACQUIRED_AT);

        assertThat(source.kind()).isEqualTo(SourceKind.FIELD_RECORD);
        assertThat(source.reference()).isEqualTo("F-118");
        assertThat(source.acquiredAt()).isEqualTo(ACQUIRED_AT);
    }

    @Test
    void 参照は200文字ちょうどまで受け付ける() {
        assertThat(new Source(SourceKind.MANUAL_ENTRY, "a".repeat(200), ACQUIRED_AT).reference())
                .hasSize(200);
    }

    @Test
    void 参照が201文字なら拒否する() {
        assertThatThrownBy(() -> new Source(SourceKind.MANUAL_ENTRY, "a".repeat(201), ACQUIRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200 文字");
    }

    @Test
    void 参照が空や空白だけなら拒否する() {
        assertThatThrownBy(() -> new Source(SourceKind.MANUAL_ENTRY, "", ACQUIRED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Source(SourceKind.MANUAL_ENTRY, "   ", ACQUIRED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 種類と参照と取得時刻のどれかがなければ拒否する() {
        assertThatThrownBy(() -> new Source(null, "F-118", ACQUIRED_AT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Source(SourceKind.FIELD_RECORD, null, ACQUIRED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Source(SourceKind.FIELD_RECORD, "F-118", null))
                .isInstanceOf(NullPointerException.class);
    }
}
