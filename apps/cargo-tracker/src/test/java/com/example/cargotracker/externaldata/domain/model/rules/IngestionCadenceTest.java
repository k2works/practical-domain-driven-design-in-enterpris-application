package com.example.cargotracker.externaldata.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** FRS-04（承認済みの情報源の取込は 1 日 1 回以上。当日は日本時間の 0:00 から。W10-1 計画 CP-6）。 */
class IngestionCadenceTest {

    @Test
    void 当日の始まりは日本時間の0時() {
        // 2026-11-27 11:00 JST
        assertThat(IngestionCadence.startOfDay(Instant.parse("2026-11-27T02:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-26T15:00:00Z"));
    }

    @Test
    void 日本時間の0時ちょうどはその日の始まりで直前は前の日() {
        assertThat(IngestionCadence.startOfDay(Instant.parse("2026-11-26T15:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-26T15:00:00Z"));
        assertThat(IngestionCadence.startOfDay(Instant.parse("2026-11-26T14:59:59Z")))
                .isEqualTo(Instant.parse("2026-11-25T15:00:00Z"));
    }
}
