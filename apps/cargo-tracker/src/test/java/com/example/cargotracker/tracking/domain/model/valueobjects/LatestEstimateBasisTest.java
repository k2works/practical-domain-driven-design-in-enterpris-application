package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis.Kind;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 見込みの根拠の値（Bolt 39g4c の行 2）。実績番号は種類が実績のときだけ持ち、1 以上。 */
class LatestEstimateBasisTest {

    @Test
    void 行2a_当初の到着予定は実績番号を持たない() {
        assertThat(LatestEstimateBasis.originalEta())
                .isEqualTo(new LatestEstimateBasis(Kind.ORIGINAL_ETA, OptionalInt.empty()));
    }

    @Test
    void 行2b_実績は実績番号を持つ() {
        assertThat(LatestEstimateBasis.milestone(3).milestoneNo()).hasValue(3);
        assertThat(LatestEstimateBasis.milestone(3).kind()).isEqualTo(Kind.MILESTONE);
    }

    static Stream<Arguments> 行2cde() {
        return Stream.of(
                Arguments.of("(c) 実績で実績番号なし", Kind.MILESTONE, OptionalInt.empty()),
                Arguments.of("(d) 当初の到着予定で実績番号あり", Kind.ORIGINAL_ETA, OptionalInt.of(1)),
                Arguments.of("(d) 航海の到着予定で実績番号あり", Kind.VOYAGE_ARRIVAL, OptionalInt.of(1)),
                Arguments.of("(e) 実績で実績番号 0", Kind.MILESTONE, OptionalInt.of(0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void 行2cde(String row, Kind kind, OptionalInt milestoneNo) {
        assertThatThrownBy(() -> new LatestEstimateBasis(kind, milestoneNo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
