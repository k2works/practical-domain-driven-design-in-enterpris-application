package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TransportRequestNumberTest {

    @ParameterizedTest
    @CsvSource({
        "2026, 1, TR-2026-0001",
        "2026, 142, TR-2026-0142",
        "2026, 9999, TR-2026-9999",
        "2026, 10000, TR-2026-10000"
    })
    void 連番を4桁のゼロ埋めで表記し9999を超えたら桁を増やす(int year, int sequence, String text) {
        assertThat(new TransportRequestNumber(year, sequence).text()).isEqualTo(text);
    }

    @ParameterizedTest
    @CsvSource({"2026-12-31T14:59:59Z, 2026", "2026-12-31T15:00:00Z, 2027", "2027-01-01T00:00:00Z, 2027"})
    void 年は提出時刻の日本時間の年にする(String submittedAt, int year) {
        assertThat(TransportRequestNumber.yearOf(new UtcInstant(Instant.parse(submittedAt))))
                .isEqualTo(year);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TR-2026-0001", "TR-2026-10000"})
    void 表記から読み直すと同じ業務番号になる(String text) {
        assertThat(TransportRequestNumber.parse(text).text()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "TR-2026-1", "TR-26-0001", "tr-2026-0001", "TR-2026-0000", "TR-2026-00001"})
    void 業務番号の形式でない表記は読めない(String text) {
        assertThatThrownBy(() -> TransportRequestNumber.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 連番は1から始まる() {
        assertThatThrownBy(() -> new TransportRequestNumber(2026, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 年は4桁である() {
        assertThatThrownBy(() -> new TransportRequestNumber(999, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransportRequestNumber(10000, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
