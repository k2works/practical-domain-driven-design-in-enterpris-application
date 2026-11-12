package com.example.cargotracker.externaldata.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.rules.DiscrepancyRule.Discrepancy;
import com.example.cargotracker.externaldata.domain.model.valueobjects.DiscrepancyReason;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ExternalIdAdoptedValue;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 差異の規則（US-14 AC5、ED-INV-03。Bolt 39h の状態の組み合わせの行 1。開始準備の決定 (3)・(4)、【業務の判断】(ay)〜(ba)）。今受け取った原本の
 * 対象のキーと取得時刻と、この外部 ID の採用値の一覧から、差異の理由と比べた外部版を決める。取得時刻は G1 &lt; G2 &lt; G3、追跡番号は CTA・CTB、
 * 航海の対象のキーは航海番号 V100。期待値は決定の文から導いた（Bolt 39g4 の Try）。
 */
class DiscrepancyRuleTest {

    private static final Instant G1 = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant G2 = Instant.parse("2026-11-03T08:30:00Z");
    private static final Instant G3 = Instant.parse("2026-11-03T09:00:00Z");
    /** 公開した時刻の基点（取込の時刻）。 */
    private static final Instant PUBLISHED = Instant.parse("2026-11-03T10:00:00Z");

    private static final String CTA = "CTABCDEFGH2345";
    private static final String CTB = "CTABCDEFGH2346";
    private static final String VOYAGE = "V100";

    private static ExternalIdAdoptedValue adopted(
            String version, String targetKey, Instant acquiredAt, Instant publishedAt) {
        return new ExternalIdAdoptedValue(version, targetKey, acquiredAt, publishedAt, UUID.randomUUID());
    }

    private static ExternalIdAdoptedValue adopted(String version, String targetKey, Instant acquiredAt) {
        return adopted(version, targetKey, acquiredAt, PUBLISHED);
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of("(a) 一覧が空", CTA, G2, List.of(), null, null),
                Arguments.of("(b) G1・CTA に G2・CTA", CTA, G2, List.of(adopted("1", CTA, G1)), null, null),
                Arguments.of("(c) G2・CTA に同じ取得時刻の G2・CTA", CTA, G2, List.of(adopted("1", CTA, G2)), null, null),
                Arguments.of(
                        "(d) G2・CTA に G1・CTA",
                        CTA,
                        G1,
                        List.of(adopted("2", CTA, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                Arguments.of(
                        "(e) G1・CTA に G2・CTB",
                        CTB,
                        G2,
                        List.of(adopted("1", CTA, G1)),
                        DiscrepancyReason.TARGET_KEY_DIFFERS,
                        "1"),
                Arguments.of(
                        "(f) G2・CTA に G1・CTB は逆転を先に見る",
                        CTB,
                        G1,
                        List.of(adopted("2", CTA, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                Arguments.of(
                        "(g) 航海の G2 に G1",
                        VOYAGE,
                        G1,
                        List.of(adopted("2", VOYAGE, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                Arguments.of("(h1) 外部版 9・G1 に外部版 10・G2", CTA, G2, List.of(adopted("9", CTA, G1)), null, null),
                Arguments.of("(h2) 外部版 2・G1 に外部版 1・G2", CTA, G2, List.of(adopted("2", CTA, G1)), null, null),
                // 外部版の文字列の順で最後（"9"）でなく、取得時刻の最も遅い "10" と比べる（変異 M4 を規則の層で縛る。39g4c の Try）
                Arguments.of(
                        "(h3) 外部版 10・G3 と外部版 9・G1 に G2",
                        CTA,
                        G2,
                        List.of(adopted("10", CTA, G3), adopted("9", CTA, G1)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "10"),
                Arguments.of(
                        "(i) G2 に 1 マイクロ秒前",
                        CTA,
                        G2.minusNanos(1_000),
                        List.of(adopted("2", CTA, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                // 公開した時刻の順で最も新しい V1 でなく、最後に取得した V3 と比べる（決定 (3)。変異 M10 を規則の層で縛る）
                Arguments.of(
                        "(j) V1・G1（後に公開）と V3・G3（先に公開）に G2・CTA",
                        CTA,
                        G2,
                        List.of(adopted("1", CTA, G1, PUBLISHED.plusSeconds(3600)), adopted("3", CTA, G3, PUBLISHED)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "3"),
                // 矛盾の相手は公開した時刻の順で最も新しい V1（CTA）。取得時刻の順の V2（CTB）ではない（D-105 の順）
                Arguments.of(
                        "(k) V1・G1・CTA（後に公開）と V2・G1・CTB（先に公開）に G2・CTA",
                        CTA,
                        G2,
                        List.of(adopted("1", CTA, G1, PUBLISHED.plusSeconds(3600)), adopted("2", CTB, G1, PUBLISHED)),
                        null,
                        null),
                Arguments.of(
                        "(k2) 同じ組に G2・CTB",
                        CTB,
                        G2,
                        List.of(adopted("1", CTA, G1, PUBLISHED.plusSeconds(3600)), adopted("2", CTB, G1, PUBLISHED)),
                        DiscrepancyReason.TARGET_KEY_DIFFERS,
                        "1"));
    }

    @ParameterizedTest(name = "行1 {0} → {4} {5}")
    @MethodSource("rows")
    void 行1_差異の理由と比べた外部版を決める(
            String name,
            String targetKey,
            Instant acquiredAt,
            List<ExternalIdAdoptedValue> adopted,
            DiscrepancyReason reason,
            String comparedVersion) {
        Optional<Discrepancy> discrepancy = DiscrepancyRule.discrepancyOf(targetKey, acquiredAt, adopted);

        if (reason == null) {
            assertThat(discrepancy).isEmpty();
        } else {
            assertThat(discrepancy).contains(new Discrepancy(reason, comparedVersion));
        }
    }
}
