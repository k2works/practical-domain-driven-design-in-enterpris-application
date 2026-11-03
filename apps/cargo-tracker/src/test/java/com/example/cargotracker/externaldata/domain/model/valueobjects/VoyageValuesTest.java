package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 航海の値（Bolt 39b2b）。航海番号（1〜30 文字）と寄港の値の並び（2 つ以上、重ねない）。経路設計の航海の規則の写し。 */
class VoyageValuesTest {

    private static final PortCallValue TOKYO_DEPARTURE =
            new PortCallValue(new Location("JPTYO"), null, at("2026-11-19T23:00:00Z"));
    private static final PortCallValue LOS_ANGELES_ARRIVAL =
            new PortCallValue(new Location("USLAX"), at("2026-12-01T14:00:00Z"), null);

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }

    @Test
    void 行2_寄港が2つの航海の値を受ける() {
        VoyageValues values = new VoyageValues("V2611E", List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL));

        assertThat(values.voyageNumber()).isEqualTo("V2611E");
        assertThat(values.portCalls()).containsExactly(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL);
        assertThat(values).isInstanceOf(RecordValues.class);
    }

    @Test
    void 行2_寄港が1つの航海の値は作れない() {
        assertThatThrownBy(() -> new VoyageValues("V2611E", List.of(TOKYO_DEPARTURE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("寄港が足りません（2 つ以上）");
    }

    @Test
    void 行7_同じ港と同じ到着と同じ出発の寄港を重ねた航海の値は作れない() {
        PortCallValue sameAgain = new PortCallValue(new Location("JPTYO"), null, at("2026-11-19T23:00:00Z"));

        assertThatThrownBy(() -> new VoyageValues("V2611E", List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL, sameAgain)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同じ寄港があります");
    }

    @Test
    void 行7_同じ港で時刻の違う寄港は周回の航路として受ける() {
        PortCallValue tokyoAgain = new PortCallValue(new Location("JPTYO"), at("2026-12-15T01:00:00Z"), null);

        assertThat(new VoyageValues("V2611E", List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL, tokyoAgain)).portCalls())
                .hasSize(3);
    }

    @Test
    void 行14_航海番号は30コードポイントまで受け31は作れない() {
        String thirtyWithSupplementary = "V".repeat(29) + "𠀋";

        assertThat(new VoyageValues("V".repeat(30), List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL)).voyageNumber())
                .hasSize(30);
        assertThat(new VoyageValues(thirtyWithSupplementary, List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL))
                        .voyageNumber())
                .isEqualTo(thirtyWithSupplementary);
        assertThatThrownBy(() -> new VoyageValues("V".repeat(31), List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行24_航海番号のNULLと空と空白だけと寄港の並びのNULLと空を拒む() {
        List<PortCallValue> portCalls = List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL);

        assertThatThrownBy(() -> new VoyageValues(null, portCalls)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VoyageValues("", portCalls)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoyageValues(" ", portCalls)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoyageValues("V2611E", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VoyageValues("V2611E", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行24_寄港の並びは写しを持ち後から変えられない() {
        List<PortCallValue> source = new ArrayList<>(List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL));
        VoyageValues values = new VoyageValues("V2611E", source);

        source.clear();

        assertThat(values.portCalls()).hasSize(2);
        assertThatThrownBy(() -> values.portCalls().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 行2_寄港の列の誤りは行の誤りで寄港の書き方を示す() {
        FormatDefect defect = FormatDefect.INVALID_PORT_CALLS;

        assertThat(defect.occursIn(FormatDefect.Extent.ROW)).isTrue();
        assertThat(defect.occursIn(FormatDefect.Extent.FILE)).isFalse();
        assertThat(defect.rowNote(new PhysicalLineRange(5, 5), List.of("port_calls", "寄港が足りません（2 つ以上）")))
                .isEqualTo("5 行目: 寄港の列の形が違います（port_calls、寄港が足りません（2 つ以上））。"
                        + "寄港は 2 つ以上を | で区切り、各寄港は UN/LOCODE;到着;出発 で到着か出発を書き、出発は到着以後にし、"
                        + "同じ寄港を重ねないでください。");
    }
}
