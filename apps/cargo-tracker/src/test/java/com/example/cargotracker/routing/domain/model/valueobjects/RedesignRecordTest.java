package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 再設計要の記録（US-07 AC4 の準備。Bolt 37d）。時刻と、理由の区分の 1 つ以上。
 */
class RedesignRecordTest {

    static final UtcInstant REQUIRED_AT = new UtcInstant(Instant.parse("2026-10-09T00:00:00Z"));

    @Test
    void 時刻と理由の区分を順に持つ() {
        List<RedesignReasonCode> reasons =
                new ArrayList<>(List.of(RedesignReasonCode.CONNECTION_TOO_SHORT, RedesignReasonCode.DEADLINE_EXCEEDED));

        RedesignRecord redesign = new RedesignRecord(REQUIRED_AT, reasons);
        reasons.clear();

        assertThat(redesign.requiredAt()).isEqualTo(REQUIRED_AT);
        assertThat(redesign.reasons())
                .containsExactly(RedesignReasonCode.CONNECTION_TOO_SHORT, RedesignReasonCode.DEADLINE_EXCEEDED);
    }

    @Test
    void 同じ理由の区分を重ねて持てない() {
        // 保存の列（VARCHAR(200)）と DE-06 の理由の語彙で、区分は 1 度ずつ（Bolt 37d の開発レビュー）
        List<RedesignReasonCode> twice =
                List.of(RedesignReasonCode.DEADLINE_EXCEEDED, RedesignReasonCode.DEADLINE_EXCEEDED);

        assertThatThrownBy(() -> new RedesignRecord(REQUIRED_AT, twice)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 理由がなければ作れない() {
        List<RedesignReasonCode> none = List.of();

        assertThatThrownBy(() -> new RedesignRecord(REQUIRED_AT, none)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 時刻がなければ作れない() {
        List<RedesignReasonCode> reasons = List.of(RedesignReasonCode.VOYAGE_UNAVAILABLE);

        assertThatThrownBy(() -> new RedesignRecord(null, reasons)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 理由の区分の名前は除外理由の区分と同じものがある() {
        // 期限超過・接続不足・接続できないは制約適合判定の理由から写す（Bolt 37d2）
        assertThat(List.of(
                        RedesignReasonCode.DEADLINE_EXCEEDED,
                        RedesignReasonCode.CONNECTION_TOO_SHORT,
                        RedesignReasonCode.NOT_CONNECTABLE))
                .extracting(Enum::name)
                .allSatisfy(
                        name -> assertThat(ExclusionReasonCode.valueOf(name)).isNotNull());
    }
}
