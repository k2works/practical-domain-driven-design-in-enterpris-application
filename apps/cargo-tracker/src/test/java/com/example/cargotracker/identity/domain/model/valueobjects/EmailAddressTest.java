package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @Test
    void 前後の空白を除き小文字にする() {
        assertThat(EmailAddress.of("  Shipper@Example.COM ").value()).isEqualTo("shipper@example.com");
    }

    @Test
    void 大文字と小文字の違うメールアドレスは同じ() {
        assertThat(EmailAddress.of("Sales@A-Corp.example")).isEqualTo(EmailAddress.of("sales@a-corp.example"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "no-at-mark.example", "@example.com", "user@"})
    void 空やアットマークの前後がないものは受け付けない(String input) {
        assertThatThrownBy(() -> EmailAddress.of(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 長さは320文字まで() {
        String local = "a".repeat(64);
        String atMax = local + "@" + "d".repeat(EmailAddress.MAX_LENGTH - local.length() - 1);
        String overMax = local + "@" + "d".repeat(EmailAddress.MAX_LENGTH - local.length());

        assertThat(EmailAddress.of(atMax).value()).hasSize(EmailAddress.MAX_LENGTH);
        assertThatThrownBy(() -> EmailAddress.of(overMax)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 正規化していない値では直接作れない() {
        assertThatThrownBy(() -> new EmailAddress("Shipper@example.com")).isInstanceOf(IllegalArgumentException.class);
    }
}
