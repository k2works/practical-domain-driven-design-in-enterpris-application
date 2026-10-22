package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 再設計要の理由の区分の写し（制約適合判定の理由の区分から。US-07 AC4。Bolt 37d2）。 */
class RedesignReasonCodeTest {

    @ParameterizedTest(name = "{0} は同じ名前の再設計要の理由の区分に写す")
    @EnumSource(
            value = ExclusionReasonCode.class,
            names = {"DEADLINE_EXCEEDED", "CONNECTION_TOO_SHORT", "NOT_CONNECTABLE"})
    void 期限超過と接続不足と接続できないは同じ名前の区分に写す(ExclusionReasonCode code) {
        assertThat(RedesignReasonCode.from(code).name()).isEqualTo(code.name());
    }

    @ParameterizedTest(name = "{0} は評価に出ないので写さない")
    @EnumSource(
            value = ExclusionReasonCode.class,
            names = {"CARGO_NOT_SUPPORTED", "INFO_INSUFFICIENT"})
    void 貨物種別と情報不足は評価の前提が崩れているので写さない(ExclusionReasonCode code) {
        assertThatThrownBy(() -> RedesignReasonCode.from(code)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 航海を引き直せないは除外理由の区分にない() {
        assertThat(ExclusionReasonCode.values())
                .extracting(ExclusionReasonCode::name)
                .doesNotContain(RedesignReasonCode.VOYAGE_UNAVAILABLE.name());
    }
}
