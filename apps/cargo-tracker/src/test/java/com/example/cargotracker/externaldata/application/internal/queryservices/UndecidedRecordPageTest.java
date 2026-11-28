package com.example.cargotracker.externaldata.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 判断を待つ隔離・差異の一覧の行の型（Bolt 39h3 の行 7）。一覧の行はいつも取込を持つ（「この取込で絞る」の行き先）。
 */
class UndecidedRecordPageTest {

    @Test
    void 行7_a_取込IDがnullの行は作れない() {
        assertThatNullPointerException()
                .isThrownBy(() -> new UndecidedRecordPage.Item(
                        UUID.randomUUID(),
                        null,
                        Instant.parse("2026-11-16T01:05:00Z"),
                        "開発海運（架空）",
                        "VOYAGE",
                        new PhysicalLineRange(2, 2),
                        Classification.QUARANTINED,
                        null,
                        null,
                        "2 行目: 注記"))
                .withMessageContaining("ingestionId");
    }
}
