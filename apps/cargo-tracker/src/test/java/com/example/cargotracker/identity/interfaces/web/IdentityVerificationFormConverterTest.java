package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 本人確認の日時の欄の解釈（Bolt 38c。行 7。S-13 と同じ日本時間のテキストの欄を厳格に解釈する）。 */
class IdentityVerificationFormConverterTest {

    @Test
    void 日本時間の年月日と時刻をUTCの時点にし前後の空白を除く() {
        assertThat(IdentityVerificationFormConverter.verifiedAt(" 2026-11-09 10:30 "))
                .contains(new UtcInstant(Instant.parse("2026-11-09T01:30:00Z")));
    }

    @ParameterizedTest(name = "\"{0}\" は形の誤り")
    @ValueSource(
            strings = {
                "2026/11/09 10:30",
                "2026-11-09 9:30",
                "2026-11-09T10:30",
                "2026-02-30 10:00",
                "2026-11-09 24:00",
                "きのう"
            })
    void 形の誤りと存在しない日時は空(String text) {
        assertThat(IdentityVerificationFormConverter.verifiedAt(text)).isEmpty();
    }

    @Test
    void 登録の時刻はISO8601のUTCを時点にしないものと形の誤りは空() {
        // 確認の画面が hidden で持つ値（開発レビュー L-5。入力の解釈は FormConverter に置く）
        assertThat(IdentityVerificationFormConverter.enrolledAt("2026-10-01T00:00:00.123456Z"))
                .contains(new UtcInstant(Instant.parse("2026-10-01T00:00:00.123456Z")));
        assertThat(IdentityVerificationFormConverter.enrolledAt(null)).isEmpty();
        assertThat(IdentityVerificationFormConverter.enrolledAt("x")).isEmpty();
    }
}
