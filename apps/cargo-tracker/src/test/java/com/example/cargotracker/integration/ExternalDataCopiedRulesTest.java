package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 写しを守るテスト（Bolt 39b2b）。外部データは追跡・経路設計の型を参照しない（モジュールの規則）ので、追跡番号の形・実績の種類・寄港の
 * 規則を写しとして持つ。写しが元の規則と食い違えば落ちる。比べるのは振る舞い（同じ入力を受けるか拒むか）で、正規表現の書き方や
 * 例外の文言は比べない。
 *
 * <p>寄港の重複（同じ港・同じ到着・同じ出発の寄港を重ねない）の規則は外部データの {@code VoyageValues} の片側だけにあり、経路設計の
 * 航海は確かめていないので、このテストは守らない（入力の寄港はどれも互いに違う）。経路設計の航海の規則を足す 39e で、写しとこのテストを
 * 直す。
 */
class ExternalDataCopiedRulesTest {

    /** 正しい追跡番号。3 文字目と 14 文字目を差し替えて比べる。 */
    private static final String VALID_TRACKING_NUMBER = "CTABCDEFGH2345";

    private static final String UPPER_CASE_LETTERS_AND_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final UtcInstant ARRIVAL = at("2026-11-19T10:00:00Z");
    private static final UtcInstant BEFORE_ARRIVAL = at("2026-11-19T09:59:59Z");
    private static final UtcInstant AFTER_ARRIVAL = at("2026-11-19T23:00:00Z");

    /** 港の違う寄港（重複の規則に当たらないようにする）。 */
    private static final List<String> PORTS = List.of("JPTYO", "USLAX", "SGSIN", "NLRTM");

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }

    private static boolean accepts(Runnable construction) {
        try {
            construction.run();
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    static Stream<String> trackingNumbers() {
        List<String> values = new ArrayList<>();
        for (char c : UPPER_CASE_LETTERS_AND_DIGITS.toCharArray()) {
            values.add(VALID_TRACKING_NUMBER.substring(0, 2) + c + VALID_TRACKING_NUMBER.substring(3));
            values.add(VALID_TRACKING_NUMBER.substring(0, 13) + c);
        }
        values.add(VALID_TRACKING_NUMBER.substring(0, 13)); // 13 文字
        values.add(VALID_TRACKING_NUMBER + "2"); // 15 文字
        values.add("CX" + VALID_TRACKING_NUMBER.substring(2)); // 接頭辞の違い
        values.add("ct" + VALID_TRACKING_NUMBER.substring(2)); // 小文字の接頭辞
        values.add("CT" + VALID_TRACKING_NUMBER.substring(2).toLowerCase()); // 小文字の本体
        values.add(""); // 空
        return values.stream();
    }

    @ParameterizedTest
    @MethodSource("trackingNumbers")
    void 行21_追跡の追跡番号と外部データの追跡番号の値は同じ値を受けるか拒むかが一致する(String value) {
        boolean tracking = accepts(() -> new TrackingNumber(value));

        assertThat(accepts(() -> new TrackingNumberValue(value))).as(value).isEqualTo(tracking);
    }

    @Test
    void 行21_追跡番号の比べる値には受ける値と拒む値の両方がある() {
        List<Boolean> results = trackingNumbers()
                .map(value -> accepts(() -> new TrackingNumber(value)))
                .distinct()
                .toList();

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void 行22_追跡の実績の種類と外部データの実績の種類の値は名前の集合が一致する() {
        assertThat(Arrays.stream(MilestoneKindValue.values()).map(Enum::name).collect(Collectors.toSet()))
                .isEqualTo(Arrays.stream(MilestoneKind.values()).map(Enum::name).collect(Collectors.toSet()));
    }

    static Stream<Arguments> portCallTimes() {
        return Stream.of(
                Arguments.of("到着だけ", ARRIVAL, null),
                Arguments.of("出発だけ", null, AFTER_ARRIVAL),
                Arguments.of("到着と出発の両方（出発が後）", ARRIVAL, AFTER_ARRIVAL),
                Arguments.of("到着と出発が同じ時刻", ARRIVAL, ARRIVAL),
                Arguments.of("出発が到着より前", ARRIVAL, BEFORE_ARRIVAL),
                Arguments.of("到着と出発の両方が空", null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("portCallTimes")
    void 行23_経路設計の寄港と外部データの寄港の値は同じ到着と出発を受けるか拒むかが一致する(String description, UtcInstant arrivalAt, UtcInstant departureAt) {
        Location port = new Location("JPTYO");
        boolean routing = accepts(() -> new PortCall(port, arrivalAt, departureAt));

        assertThat(accepts(() -> new PortCallValue(port, arrivalAt, departureAt)))
                .as(description)
                .isEqualTo(routing);
    }

    @ParameterizedTest(name = "寄港 {0} つ")
    @MethodSource("portCallCounts")
    void 行23_経路設計の航海と外部データの航海の値は同じ寄港の数を受けるか拒むかが一致する(int count) {
        List<PortCall> portCalls = IntStream.range(0, count)
                .mapToObj(i -> new PortCall(new Location(PORTS.get(i)), ARRIVAL, AFTER_ARRIVAL))
                .toList();
        List<PortCallValue> portCallValues = IntStream.range(0, count)
                .mapToObj(i -> new PortCallValue(new Location(PORTS.get(i)), ARRIVAL, AFTER_ARRIVAL))
                .toList();
        boolean routing = accepts(() -> new Voyage("V2611E", portCalls, "v1", ARRIVAL));

        assertThat(accepts(() -> new VoyageValues("V2611E", portCallValues)))
                .as("寄港 %d つ", count)
                .isEqualTo(routing);
    }

    static IntStream portCallCounts() {
        return IntStream.rangeClosed(0, 3);
    }

    @Test
    void 行23_寄港の比べる値には受ける値と拒む値の両方がある() {
        List<Boolean> results = Stream.concat(
                        portCallTimes().map(arguments -> {
                            Object[] values = arguments.get();
                            return accepts(() -> new PortCall(
                                    new Location("JPTYO"), (UtcInstant) values[1], (UtcInstant) values[2]));
                        }),
                        portCallCounts()
                                .mapToObj(count -> accepts(() -> new Voyage(
                                        "V2611E",
                                        IntStream.range(0, count)
                                                .mapToObj(i -> new PortCall(new Location(PORTS.get(i)), ARRIVAL, null))
                                                .toList(),
                                        "v1",
                                        ARRIVAL))))
                .distinct()
                .toList();

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }
}
