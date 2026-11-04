package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.DecisionKind;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 受信記録の判断（Bolt 39d2 の行 2・3・7・8。開始準備の決定 (2)・(3)・(10)、T-240・T-244）。却下のファクトリーは隔離の受信記録だけを判断し、
 * 理由は前後の空白を除いて 1〜1000 文字（コードポイント）、判断の時刻はマイクロ秒に切り捨てる。
 */
class ReceivedRecordDecisionTest {

    /** 補助文字（U+20BB7。UTF-16 で 2 単位）。 */
    private static final String SUPPLEMENTARY = new String(Character.toChars(0x20BB7));

    private static final Instant DECIDED_AT = Instant.parse("2026-11-16T01:05:00.123456789Z");
    private static final UserId STEWARD = new UserId(UUID.randomUUID());

    private static ReceivedRecord received(Classification classification, boolean keyless) {
        return new ReceivedRecord(
                UUID.randomUUID(),
                keyless ? null : UUID.randomUUID(),
                UUID.randomUUID(),
                PayloadHash.of(new byte[] {1}),
                PayloadObjectKey.newRecordPayload(),
                keyless ? null : Instant.parse("2026-11-16T00:00:00Z"),
                classification,
                "注記",
                Instant.parse("2026-11-16T01:00:00Z"),
                new PhysicalLineRange(4, 4));
    }

    @Test
    void 行1_行2_隔離を却下すると判断者と理由を持ち時刻をマイクロ秒に切り捨てた却下の判断になる() {
        ReceivedRecord keyless = received(Classification.QUARANTINED, true);

        ReceivedRecordDecision decision =
                ReceivedRecordDecision.reject(keyless, "直したファイルを取り込み直した。", STEWARD, DECIDED_AT);

        assertThat(decision.id()).isNotNull();
        assertThat(decision.receivedRecordId()).isEqualTo(keyless.id());
        assertThat(decision.decision()).isEqualTo(DecisionKind.REJECTED);
        assertThat(decision.decidedBy()).isEqualTo(STEWARD);
        assertThat(decision.decidedAt()).isEqualTo(Instant.parse("2026-11-16T01:05:00.123456Z"));
        assertThat(decision.reason()).isEqualTo("直したファイルを取り込み直した。");
    }

    @Test
    void 行1_内容の違う原本の隔離も却下でき理由の前後の空白は除く() {
        ReceivedRecord mismatch = received(Classification.QUARANTINED, false);

        ReceivedRecordDecision decision = ReceivedRecordDecision.reject(mismatch, "  提供元に確かめた。\n", STEWARD, DECIDED_AT);

        assertThat(decision.reason()).isEqualTo("提供元に確かめた。");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Classification.class)
    void 行3_判断できるのは分類が隔離の受信記録だけで分類のすべてで決まる(Classification classification) {
        // 差異（DISCREPANCY）は 39h で分類に足し、そのとき判断できるようにする（T-240。分類のすべての行を縛る）
        boolean expected = EnumSet.of(Classification.QUARANTINED).contains(classification);
        ReceivedRecord received = received(classification, false);

        assertThat(ReceivedRecordDecision.decidable(received)).isEqualTo(expected);
        if (expected) {
            assertThat(ReceivedRecordDecision.reject(received, "理由", STEWARD, DECIDED_AT))
                    .isNotNull();
        } else {
            assertThatThrownBy(() -> ReceivedRecordDecision.reject(received, "理由", STEWARD, DECIDED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(classification.name());
        }
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " ", "　", "\n\t"})
    void 行7_却下の理由が空か空白だけなら却下を作らない(String reason) {
        ReceivedRecord quarantined = received(Classification.QUARANTINED, true);

        assertThatThrownBy(() -> ReceivedRecordDecision.reject(quarantined, reason, STEWARD, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceivedRecordDecision.reject(quarantined, null, STEWARD, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行8_補助文字を含む1000文字の理由は却下でき1001文字は作らない() {
        ReceivedRecord quarantined = received(Classification.QUARANTINED, true);
        String thousand = SUPPLEMENTARY.repeat(500) + "あ".repeat(500);
        String thousandAndOne = thousand + "い";

        assertThat(ReceivedRecordDecision.reject(quarantined, thousand, STEWARD, DECIDED_AT)
                        .reason())
                .isEqualTo(thousand);
        assertThatThrownBy(() -> ReceivedRecordDecision.reject(quarantined, thousandAndOne, STEWARD, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ReceivedRecordDecision.REASON_LIMIT).isEqualTo(1000);
    }

    @Test
    void 判断は受信記録と種類と判断者と時刻を欠かせない() {
        UUID id = UUID.randomUUID();
        UUID receivedRecordId = UUID.randomUUID();

        assertThatThrownBy(() -> new ReceivedRecordDecision(
                        null, receivedRecordId, DecisionKind.REJECTED, STEWARD, DECIDED_AT, "理由"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReceivedRecordDecision(id, null, DecisionKind.REJECTED, STEWARD, DECIDED_AT, "理由"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReceivedRecordDecision(id, receivedRecordId, null, STEWARD, DECIDED_AT, "理由"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new ReceivedRecordDecision(id, receivedRecordId, DecisionKind.REJECTED, null, DECIDED_AT, "理由"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new ReceivedRecordDecision(id, receivedRecordId, DecisionKind.REJECTED, STEWARD, null, "理由"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReceivedRecordDecision(
                        id, receivedRecordId, DecisionKind.REJECTED, STEWARD, DECIDED_AT, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
