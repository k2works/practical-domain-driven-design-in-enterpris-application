package com.example.cargotracker.identity.infrastructure.devlogin;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.identity.infrastructure.crypto.JavaOtpTotpCodeGenerator;
import com.example.cargotracker.identity.infrastructure.crypto.TestSecretCiphers;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * dev の A-02 の入力済みのコード（Bolt 31、ADR-011 の dev の 3 層目）。設定の開発用の固定の秘密（Base32）だけからコードを作り、
 * 利用者の表を読まない。開発用の利用者でない主体には何も作らない。
 */
class DevTotpCodesTest {

    /** RFC 6238 の付録 B の時刻 59 秒（刻み 1）。秘密 "12345678901234567890" の 6 桁は 287082。 */
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);

    private final AesGcmSecretCipher cipher = TestSecretCiphers.cipher();
    private final DevTotpCodes codes = new DevTotpCodes(
            new DevLoginProperties(List.of(new DevLoginProperties.Account(
                    Role.SALES, "sales@dev.example", "dev-password", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))),
            cipher,
            new JavaOtpTotpCodeGenerator(cipher),
            CLOCK);

    @Test
    void 開発用の利用者には設定の固定の秘密からその時点のコードを作る() {
        assertThat(TotpCredential.stepAt(new UtcInstant(CLOCK.instant()))).isEqualTo(1);
        assertThat(codes.codeFor("sales@dev.example")).contains("287082");
    }

    @Test
    void メールアドレスは大文字と前後の空白を問わない() {
        assertThat(codes.codeFor(" SALES@dev.example ")).contains("287082");
    }

    @Test
    void 開発用の利用者でない主体には何も作らない() {
        assertThat(codes.codeFor("someone@example.com")).isEmpty();
        assertThat(codes.codeFor(null)).isEmpty();
    }
}
