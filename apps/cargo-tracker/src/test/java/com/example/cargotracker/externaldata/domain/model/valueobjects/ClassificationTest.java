package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 分類の規則（Bolt 39b の行 1〜3。ED-INV-02、BR-12）。既にある外部原本の基準のハッシュ（採用中の payload のハッシュ）と、その外部版に採用値が
 * あるかと、今のハッシュと、差異の理由（Bolt 39h）から分類を決める。
 */
class ClassificationTest {

    private static final PayloadHash FIRST = PayloadHash.of("MS-0001,1,TRK-0001".getBytes(StandardCharsets.UTF_8));
    private static final PayloadHash CHANGED = PayloadHash.of("MS-0001,1,TRK-0002".getBytes(StandardCharsets.UTF_8));
    private static final Optional<DiscrepancyReason> NO_REASON = Optional.empty();

    @Test
    void 行1_外部原本のない冪等性キーは採用() {
        assertThat(Classification.classify(Optional.empty(), false, FIRST, NO_REASON))
                .isEqualTo(Classification.ADOPTED);
    }

    @Test
    void 行2_基準のハッシュと同じなら重複() {
        assertThat(Classification.classify(Optional.of(FIRST), true, FIRST, NO_REASON))
                .isEqualTo(Classification.DUPLICATE);
    }

    @Test
    void 行3_基準のハッシュと違えば隔離() {
        assertThat(Classification.classify(Optional.of(FIRST), true, CHANGED, NO_REASON))
                .isEqualTo(Classification.QUARANTINED);
    }

    /** Bolt 39h の行 2（開始準備の決定 (6)、【業務の判断】(bc)）。外部原本の基準のハッシュ・その外部版の採用値の有無・差異の理由の組。 */
    @Nested
    class Bolt39h_差異の分類 {

        private static final Optional<DiscrepancyReason> REVERSED = Optional.of(DiscrepancyReason.VERSION_REVERSED);

        static Stream<Arguments> rows() {
            return Stream.of(
                    Arguments.of("(a) 外部原本なし・理由なし", Optional.empty(), false, FIRST, NO_REASON, Classification.ADOPTED),
                    Arguments.of(
                            "(b) 外部原本なし・理由あり", Optional.empty(), false, FIRST, REVERSED, Classification.DISCREPANCY),
                    Arguments.of(
                            "(c) 採用値のある外部版・同じハッシュ・理由あり",
                            Optional.of(FIRST),
                            true,
                            FIRST,
                            REVERSED,
                            Classification.DUPLICATE),
                    Arguments.of(
                            "(d) 採用値のある外部版・違うハッシュ・理由あり",
                            Optional.of(FIRST),
                            true,
                            CHANGED,
                            REVERSED,
                            Classification.QUARANTINED),
                    Arguments.of(
                            "(e) 採用値のない外部版・同じハッシュ・理由あり",
                            Optional.of(FIRST),
                            false,
                            FIRST,
                            REVERSED,
                            Classification.DISCREPANCY),
                    Arguments.of(
                            "(f) 採用値のない外部版・違うハッシュ・理由あり",
                            Optional.of(FIRST),
                            false,
                            CHANGED,
                            Optional.of(DiscrepancyReason.TARGET_KEY_DIFFERS),
                            Classification.DISCREPANCY),
                    Arguments.of(
                            "(g) 採用値のない外部版・同じハッシュ・理由なし",
                            Optional.of(FIRST),
                            false,
                            FIRST,
                            NO_REASON,
                            Classification.DUPLICATE),
                    Arguments.of(
                            "(h) 採用値のない外部版・違うハッシュ・理由なし",
                            Optional.of(FIRST),
                            false,
                            CHANGED,
                            NO_REASON,
                            Classification.QUARANTINED));
        }

        @ParameterizedTest(name = "行2 {0} → {5}")
        @MethodSource("rows")
        void 行2_分類を決める(
                String name,
                Optional<PayloadHash> base,
                boolean versionAdopted,
                PayloadHash current,
                Optional<DiscrepancyReason> reason,
                Classification expected) {
            assertThat(Classification.classify(base, versionAdopted, current, reason))
                    .isEqualTo(expected);
        }
    }
}
