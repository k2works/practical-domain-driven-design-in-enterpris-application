package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EncryptedSecretTest {

    @Test
    void 暗号文を持つ() {
        assertThat(new EncryptedSecret("ciphertext").value()).isEqualTo("ciphertext");
    }

    @Test
    void 空の暗号文は失敗へ倒す() {
        assertThatThrownBy(() -> new EncryptedSecret(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedSecret(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 表の列より長い暗号文は失敗へ倒す() {
        String tooLong = "x".repeat(EncryptedSecret.MAX_LENGTH + 1);

        assertThatThrownBy(() -> new EncryptedSecret(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 文字列にしても暗号文を出さない() {
        assertThat(new EncryptedSecret("ciphertext").toString()).doesNotContain("ciphertext");
    }
}
