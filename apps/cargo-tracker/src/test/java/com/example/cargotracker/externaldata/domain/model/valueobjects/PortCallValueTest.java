package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 寄港の値（Bolt 39b2b）。経路設計の寄港の規則の写し（到着か出発の少なくとも一方、出発は到着以後）。 */
class PortCallValueTest {

    private static final Location TOKYO = new Location("JPTYO");

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }

    @Test
    void 行5_到着か出発の一方だけか両方を持つ寄港を受け出発と到着が同じ時刻も受ける() {
        assertThat(new PortCallValue(TOKYO, null, at("2026-11-19T23:00:00Z")).arrivalAt())
                .isNull();
        assertThat(new PortCallValue(TOKYO, at("2026-11-19T23:00:00Z"), null).departureAt())
                .isNull();
        assertThat(new PortCallValue(TOKYO, at("2026-11-19T23:00:00Z"), at("2026-11-20T08:00:00Z")).port())
                .isEqualTo(TOKYO);
        assertThat(new PortCallValue(TOKYO, at("2026-11-19T23:00:00Z"), at("2026-11-19T23:00:00Z")).departureAt())
                .isEqualTo(at("2026-11-19T23:00:00Z"));
    }

    @Test
    void 行5_到着と出発の両方が空の寄港は作れない() {
        assertThatThrownBy(() -> new PortCallValue(TOKYO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着か出発が要ります");
    }

    @Test
    void 行5_到着より前に出発する寄港は作れない() {
        UtcInstant arrival = at("2026-11-20T08:00:00Z");
        UtcInstant departure = at("2026-11-20T07:59:59Z");

        assertThatThrownBy(() -> new PortCallValue(TOKYO, arrival, departure))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着より前に出発しています");
    }

    @Test
    void 行24_寄港の値は港のNULLを拒む() {
        UtcInstant arrival = at("2026-11-20T08:00:00Z");

        assertThatThrownBy(() -> new PortCallValue(null, arrival, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 行4_場所の形の誤りは行の誤りでUNLOCODEの書き方を示す() {
        FormatDefect defect = FormatDefect.INVALID_LOCATION;

        assertThat(defect.occursIn(FormatDefect.Extent.ROW)).isTrue();
        assertThat(defect.occursIn(FormatDefect.Extent.FILE)).isFalse();
        assertThat(defect.rowNote(new PhysicalLineRange(3, 3), List.of("port_calls", "2 つ目の寄港")))
                .isEqualTo("3 行目: 場所の形が違います（port_calls、2 つ目の寄港）。UN/LOCODE（国コード 2 文字 + 地点コード 3 文字。例 JPTYO）で書いてください。");
    }
}
