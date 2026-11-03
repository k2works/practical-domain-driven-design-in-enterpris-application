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

    /** 港は同じで時刻の違う寄港を数だけ作る（重複の規則に当たらない。到着と出発の両方を持ち、要約が最も長い形）。 */
    private static List<PortCallValue> portCalls(int count) {
        Instant base = Instant.parse("2026-11-19T00:00:00.123456Z");
        List<PortCallValue> calls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Instant arrival = base.plusSeconds(7200L * i);
            calls.add(new PortCallValue(
                    new Location("JPTYO"), new UtcInstant(arrival), new UtcInstant(arrival.plusSeconds(3600))));
        }
        return calls;
    }

    @Test
    void 行1_航海の要約は寄港を縦棒で区切りUTCのマイクロ秒6桁で空の時刻は空にする() {
        VoyageValues values = new VoyageValues("V2611E", List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL));

        assertThat(values.summary()).isEqualTo("JPTYO;;2026-11-19T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;");
    }

    @Test
    void 行11_寄港は60まで受け要約は4000コードポイントに収まる() {
        VoyageValues values = new VoyageValues("V".repeat(30), portCalls(60));

        assertThat(VoyageValues.MAX_PORT_CALLS).isEqualTo(60);
        assertThat(values.portCalls()).hasSize(60);
        String summary = values.summary();
        assertThat(summary.codePointCount(0, summary.length())).isEqualTo(3719).isLessThanOrEqualTo(4000);
    }

    @Test
    void 行11_寄港が61の航海の値は作れない() {
        List<PortCallValue> tooMany = portCalls(61);

        assertThatThrownBy(() -> new VoyageValues("V2611E", tooMany))
                .isInstanceOfSatisfying(VoyageValuesRejected.class, rejected -> {
                    assertThat(rejected.reason()).isEqualTo(VoyageValuesRejectionReason.TOO_MANY_PORT_CALLS);
                    assertThat(rejected.detail()).isEqualTo("寄港が多すぎます（60 まで）");
                });
    }

    @Test
    void 行2_寄港が1つの航海の値は作れない() {
        List<PortCallValue> oneCall = List.of(TOKYO_DEPARTURE);

        assertThatThrownBy(() -> new VoyageValues("V2611E", oneCall))
                .isInstanceOfSatisfying(VoyageValuesRejected.class, rejected -> {
                    assertThat(rejected.reason()).isEqualTo(VoyageValuesRejectionReason.TOO_FEW_PORT_CALLS);
                    assertThat(rejected.detail()).isEqualTo("寄港が足りません（2 つ以上）");
                });
    }

    @Test
    void 行7_同じ港と同じ到着と同じ出発の寄港を重ねた航海の値は作れない() {
        PortCallValue sameAgain = new PortCallValue(new Location("JPTYO"), null, at("2026-11-19T23:00:00Z"));

        List<PortCallValue> repeated = List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL, sameAgain);

        assertThatThrownBy(() -> new VoyageValues("V2611E", repeated))
                .isInstanceOfSatisfying(VoyageValuesRejected.class, rejected -> {
                    assertThat(rejected.reason()).isEqualTo(VoyageValuesRejectionReason.DUPLICATE_PORT_CALLS);
                    assertThat(rejected.detail()).isEqualTo("1 つ目と 3 つ目の寄港が同じです");
                });
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
        String tooLong = "V".repeat(31);
        List<PortCallValue> portCalls = List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL);
        assertThatThrownBy(() -> new VoyageValues(tooLong, portCalls))
                .isInstanceOfSatisfying(
                        VoyageValuesRejected.class,
                        rejected -> assertThat(rejected.reason())
                                .isEqualTo(VoyageValuesRejectionReason.VOYAGE_NUMBER_TOO_LONG));
        assertThatThrownBy(() -> VoyageValues.requireVoyageNumber(tooLong))
                .isInstanceOfSatisfying(
                        VoyageValuesRejected.class,
                        rejected -> assertThat(rejected.reason())
                                .isEqualTo(VoyageValuesRejectionReason.VOYAGE_NUMBER_TOO_LONG));
        assertThat(VoyageValues.requireVoyageNumber(thirtyWithSupplementary)).isEqualTo(thirtyWithSupplementary);
    }

    @Test
    void 行24_航海番号のNULLと空と空白だけと寄港の並びのNULLと空を拒む() {
        List<PortCallValue> portCalls = List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL);

        assertThatThrownBy(() -> new VoyageValues(null, portCalls)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VoyageValues("", portCalls)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoyageValues(" ", portCalls))
                .isInstanceOfSatisfying(
                        VoyageValuesRejected.class,
                        rejected -> assertThat(rejected.reason())
                                .isEqualTo(VoyageValuesRejectionReason.VOYAGE_NUMBER_BLANK));
        assertThatThrownBy(() -> new VoyageValues("V2611E", null)).isInstanceOf(NullPointerException.class);
        List<PortCallValue> none = List.of();
        assertThatThrownBy(() -> new VoyageValues("V2611E", none)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行24_寄港の並びは写しを持ち後から変えられない() {
        List<PortCallValue> source = new ArrayList<>(List.of(TOKYO_DEPARTURE, LOS_ANGELES_ARRIVAL));
        VoyageValues values = new VoyageValues("V2611E", source);

        source.clear();

        assertThat(values.portCalls()).hasSize(2);
        List<PortCallValue> portCalls = values.portCalls();
        assertThatThrownBy(portCalls::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 行2_寄港の列の誤りは行の誤りで原因を詳細に書き記法を前に出さずに確かめ方を示す() {
        FormatDefect defect = FormatDefect.INVALID_PORT_CALLS;

        assertThat(defect.occursIn(FormatDefect.Extent.ROW)).isTrue();
        assertThat(defect.occursIn(FormatDefect.Extent.FILE)).isFalse();
        assertThat(defect.rowNote(new PhysicalLineRange(5, 5), List.of("port_calls", "寄港が足りません（2 つ以上）")))
                .isEqualTo("5 行目: 寄港の列の形が違います（port_calls、寄港が足りません（2 つ以上））。寄港の並びを提供元に確かめてください。");
    }

    @Test
    void 行2_寄港の下限の言葉は寄港の下限の数から作る() {
        assertThat(VoyageValuesRejectionReason.TOO_FEW_PORT_CALLS.wording())
                .isEqualTo("寄港が足りません（" + VoyageValues.MIN_PORT_CALLS + " つ以上）");
    }

    @Test
    void 行3_寄港の番号を持つ拒否は寄港の番号と原因を詳細に書く() {
        assertThat(new VoyageValuesRejected(VoyageValuesRejectionReason.UNSPLITTABLE_PORT_CALL)
                        .at(3)
                        .detail())
                .isEqualTo("3 つ目の寄港: 港;到着;出発 の 3 つに区切れません");
        assertThat(new VoyageValuesRejected(VoyageValuesRejectionReason.NO_ARRIVAL_OR_DEPARTURE)
                        .at(2)
                        .detail())
                .isEqualTo("2 つ目の寄港: 到着か出発が要ります");
    }
}
